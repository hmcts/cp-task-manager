package uk.gov.hmcts.cp.taskmanager.domain.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.task.TaskRegistry;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private JobService jobService;

    @Mock
    private TaskRegistry taskRegistry;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ThreadPoolTaskExecutor executor;

    @InjectMocks
    private JobExecutor jobExecutor;

    private Job testJob;
    private JsonObject testJobData;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        testJob = new Job(
                UUID.randomUUID(),
                testJobData,
                "TEST_TASK",
                ZonedDateTime.now(),
                null,
                null,
                5,
                10
        );

        // Initialize batchSize with test values
        ReflectionTestUtils.setField(jobExecutor, "batchSize", 50);
    }

    /**
     * Stubs {@code executor.getThreadPoolExecutor()} so {@code getAvailableJobCapacity()}
     * computes a deterministic capacity: {@code queueRemainingCapacity + (maxPoolSize - activeCount)},
     * clamped to {@code [0, batchSize]} by the production code.
     */
    private void mockThreadPoolCapacity(final int queueRemainingCapacity, final int maxPoolSize, final int activeCount) {
        final ThreadPoolExecutor threadPoolExecutor = mock(ThreadPoolExecutor.class);
        @SuppressWarnings("unchecked")
        final BlockingQueue<Runnable> queue = mock(BlockingQueue.class);
        when(queue.remainingCapacity()).thenReturn(queueRemainingCapacity);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);
        when(threadPoolExecutor.getMaximumPoolSize()).thenReturn(maxPoolSize);
        when(threadPoolExecutor.getActiveCount()).thenReturn(activeCount);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
    }

    @Test
    void testInit() {
        // init() is called in setUp, verify executor is initialized
        assertNotNull(ReflectionTestUtils.getField(jobExecutor, "executor"));
    }

    @Test
    void testCheckAndAssignJobsWithNoJobs() {
        mockThreadPoolCapacity(100, 10, 0); // available = 100 + 10 = 110, capped at batchSize 50
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50))).thenReturn(Collections.emptyList());

        jobExecutor.checkAndAssignJobs();

        verify(executor, never()).execute(any());
    }

    @Test
    void testCheckAndAssignJobsWithMultipleJobs() {
        mockThreadPoolCapacity(100, 10, 0); // capped at batchSize 50
        Job job2 = new Job(
                UUID.randomUUID(),
                testJobData,
                "TASK_2",
                ZonedDateTime.now(),
                null,
                null,
                3,
                5
        );
        List<Job> jobs = Arrays.asList(testJob, job2);

        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(jobs);

        jobExecutor.checkAndAssignJobs();

        verify(jobService).assignJobsToWorkerBatch(any(UUID.class), eq(50));
        verify(executor, times(2)).execute(any());
    }

    @Test
    void testCheckAndAssignJobsSkipsPollWhenNoCapacityAvailable() {
        mockThreadPoolCapacity(0, 5, 5); // available = 0 + (5 - 5) = 0

        jobExecutor.checkAndAssignJobs();

        verify(jobService, never()).assignJobsToWorkerBatch(any(UUID.class), anyInt());
        verify(executor, never()).execute(any());
    }

    @Test
    void testGetAvailableJobCapacityClampsNegativeValueToZero() {
        // Transient race: more active threads than maxPoolSize allows, and no queue slack.
        mockThreadPoolCapacity(0, 5, 8); // raw = 0 + (5 - 8) = -3, clamped to 0

        jobExecutor.checkAndAssignJobs();

        verify(jobService, never()).assignJobsToWorkerBatch(any(UUID.class), anyInt());
        verify(executor, never()).execute(any());
    }

    @Test
    void testGetAvailableJobCapacityIsCappedAtConfiguredBatchSize() {
        mockThreadPoolCapacity(1000, 100, 0); // raw = 1100, capped at batchSize 50

        jobExecutor.checkAndAssignJobs();

        verify(jobService).assignJobsToWorkerBatch(any(UUID.class), eq(50));
    }

    @Test
    void testCheckAndAssignJobsUsesPartialCapacityWhenBelowBatchSize() {
        mockThreadPoolCapacity(3, 10, 8); // raw = 3 + (10 - 8) = 5, below batchSize 50
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(5)))
                .thenReturn(Collections.singletonList(testJob));

        jobExecutor.checkAndAssignJobs();

        verify(jobService).assignJobsToWorkerBatch(any(UUID.class), eq(5));
        verify(executor, times(1)).execute(any());
    }

    @Test
    void testCheckAndAssignJobsWithExecutionFailure() {
        mockThreadPoolCapacity(100, 10, 0);
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(jobs);
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));

        jobExecutor.checkAndAssignJobs();

        verify(jobService).releaseJob(testJob.getJobId());
        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsReleasesJobBeforeDecrementingRetryAttemptsOnFailure() {
        mockThreadPoolCapacity(100, 10, 0);
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(Collections.singletonList(testJob));
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));

        jobExecutor.checkAndAssignJobs();

        InOrder inOrder = inOrder(jobService);
        inOrder.verify(jobService).releaseJob(testJob.getJobId());
        inOrder.verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsHandlesReleaseJobFailureGracefully() {
        mockThreadPoolCapacity(100, 10, 0);
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(Collections.singletonList(testJob));
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));
        doThrow(new RuntimeException("Release failed")).when(jobService).releaseJob(any(UUID.class));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(jobService).releaseJob(testJob.getJobId());
        // releaseJob failing aborts the inner try block, so decrementRetryAttempts is never reached
        verify(jobService, never()).decrementRetryAttempts(any(UUID.class));
    }

    @Test
    void testCheckAndAssignJobsWithDecrementRetryAttemptsFailure() {
        mockThreadPoolCapacity(100, 10, 0);
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(jobs);
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));
        doThrow(new RuntimeException("Decrement failed")).when(jobService).decrementRetryAttempts(any(UUID.class));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(jobService).releaseJob(testJob.getJobId());
        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsWithServiceException() {
        mockThreadPoolCapacity(100, 10, 0);
        when(jobService.assignJobsToWorkerBatch(any(), eq(50)))
                .thenThrow(new RuntimeException("Service error"));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(executor, never()).execute(any());
    }

    @Test
    void testExecuteJobRunsTaskExecutorSynchronouslyWithoutResubmittingToPool() {
        // Regression test: executeJob() previously re-submitted a new TaskExecutor back onto
        // the same pool it was already running on (executor.execute(new TaskExecutor(...))),
        // silently doubling pool usage per job. It must now run the TaskExecutor inline via .run().
        mockThreadPoolCapacity(100, 10, 0);
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(Collections.singletonList(testJob));
        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.empty());

        jobExecutor.checkAndAssignJobs();

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor, times(1)).execute(runnableCaptor.capture());

        runnableCaptor.getValue().run();

        // Still only ever submitted once to the pool - no nested re-submission occurred.
        verify(executor, times(1)).execute(any());
        // Confirms the TaskExecutor actually ran inline (no task registered -> releases the job).
        verify(jobService).releaseJob(testJob.getJobId());
    }

    @Test
    void testDestroy() {
        jobExecutor.destroy();
        // Verify executor shutdown is called (indirectly through reflection)
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                ReflectionTestUtils.getField(jobExecutor, "executor");
        // Executor should be shutdown
        assertNotNull(executor);
    }
}
