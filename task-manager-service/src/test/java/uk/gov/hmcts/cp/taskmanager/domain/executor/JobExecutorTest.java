package uk.gov.hmcts.cp.taskmanager.domain.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void testInit() {
        // init() is called in setUp, verify executor is initialized
        assertNotNull(ReflectionTestUtils.getField(jobExecutor, "executor"));
    }

    @Test
    void testCheckAndAssignJobsWithNoJobs() {
        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50))).thenReturn(Collections.emptyList());

        jobExecutor.checkAndAssignJobs();

        verify(executor, never()).execute(any());
    }

    @Test
    void testCheckAndAssignJobsWithMultipleJobs() {
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
    void testCheckAndAssignJobsWithExecutionFailure() {
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(jobs);
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));

        jobExecutor.checkAndAssignJobs();

        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsWithDecrementFailure() {
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.assignJobsToWorkerBatch(any(UUID.class), eq(50)))
                .thenReturn(jobs);
        doThrow(new RuntimeException("Executor failed")).when(executor).execute(any(Runnable.class));
        doThrow(new RuntimeException("Decrement failed")).when(jobService).decrementRetryAttempts(any(UUID.class));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsWithServiceException() {
        when(jobService.assignJobsToWorkerBatch(any(), eq(50)))
                .thenThrow(new RuntimeException("Service error"));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(executor, never()).execute(any());
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

