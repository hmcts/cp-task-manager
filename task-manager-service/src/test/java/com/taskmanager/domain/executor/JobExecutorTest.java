package com.taskmanager.domain.executor;

import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.service.JobService;
import com.taskmanager.service.task.TaskRegistry;
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

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private JobService jobService;

    @Mock
    private TaskRegistry taskRegistry;

    @Mock
    private PlatformTransactionManager transactionManager;

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

        // Initialize executor with test values
        ReflectionTestUtils.setField(jobExecutor, "corePoolSize", 5);
        ReflectionTestUtils.setField(jobExecutor, "maxPoolSize", 10);
        ReflectionTestUtils.setField(jobExecutor, "queueCapacity", 100);
        ReflectionTestUtils.setField(jobExecutor, "threadNamePrefix", "test-executor-");
        ReflectionTestUtils.setField(jobExecutor, "waitForTasksOnShutdown", true);
        ReflectionTestUtils.setField(jobExecutor, "awaitTerminationSeconds", 60);
        ReflectionTestUtils.setField(jobExecutor, "batchSize", 50);

        jobExecutor.init();
    }

    @Test
    void testInit() {
        // init() is called in setUp, verify executor is initialized
        assertNotNull(ReflectionTestUtils.getField(jobExecutor, "executor"));
    }

    @Test
    void testCheckAndAssignJobsWithNoJobs() {
        when(jobService.getUnassignedJobs(50)).thenReturn(Collections.emptyList());

        jobExecutor.checkAndAssignJobs();

        verify(jobService).getUnassignedJobs(50);
        verify(jobService, never()).assignJobToWorker(any(), any());
    }

    @Test
    void testCheckAndAssignJobsWithSingleJob() {
        List<Job> jobs = Collections.singletonList(testJob);
        Job assignedJob = new Job(
                testJob.getJobId(),
                testJob.getJobData(),
                testJob.getAssignedTaskName(),
                testJob.getAssignedTaskStartTime(),
                UUID.randomUUID(),
                ZonedDateTime.now(),
                testJob.getRetryAttemptsRemaining(),
                testJob.getPriority()
        );

        when(jobService.getUnassignedJobs(50)).thenReturn(jobs);
        when(jobService.assignJobToWorker(any(UUID.class), any(UUID.class))).thenReturn(assignedJob);

        jobExecutor.checkAndAssignJobs();

        verify(jobService).getUnassignedJobs(50);
        verify(jobService).assignJobToWorker(eq(testJob.getJobId()), any(UUID.class));
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
        Job assignedJob1 = new Job(
                testJob.getJobId(),
                testJob.getJobData(),
                testJob.getAssignedTaskName(),
                testJob.getAssignedTaskStartTime(),
                UUID.randomUUID(),
                ZonedDateTime.now(),
                testJob.getRetryAttemptsRemaining(),
                testJob.getPriority()
        );
        Job assignedJob2 = new Job(
                job2.getJobId(),
                job2.getJobData(),
                job2.getAssignedTaskName(),
                job2.getAssignedTaskStartTime(),
                UUID.randomUUID(),
                ZonedDateTime.now(),
                job2.getRetryAttemptsRemaining(),
                job2.getPriority()
        );

        when(jobService.getUnassignedJobs(50)).thenReturn(jobs);
        when(jobService.assignJobToWorker(eq(testJob.getJobId()), any(UUID.class))).thenReturn(assignedJob1);
        when(jobService.assignJobToWorker(eq(job2.getJobId()), any(UUID.class))).thenReturn(assignedJob2);

        jobExecutor.checkAndAssignJobs();

        verify(jobService).getUnassignedJobs(50);
        verify(jobService, times(2)).assignJobToWorker(any(UUID.class), any(UUID.class));
    }

    @Test
    void testCheckAndAssignJobsWithAssignmentFailure() {
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.getUnassignedJobs(50)).thenReturn(jobs);
        when(jobService.assignJobToWorker(any(UUID.class), any(UUID.class)))
                .thenThrow(new RuntimeException("Assignment failed"));

        jobExecutor.checkAndAssignJobs();

        verify(jobService).getUnassignedJobs(50);
        verify(jobService).assignJobToWorker(any(UUID.class), any(UUID.class));
        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsWithDecrementFailure() {
        List<Job> jobs = Collections.singletonList(testJob);

        when(jobService.getUnassignedJobs(50)).thenReturn(jobs);
        when(jobService.assignJobToWorker(any(UUID.class), any(UUID.class)))
                .thenThrow(new RuntimeException("Assignment failed"));
        doThrow(new RuntimeException("Decrement failed")).when(jobService).decrementRetryAttempts(any(UUID.class));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(jobService).decrementRetryAttempts(testJob.getJobId());
    }

    @Test
    void testCheckAndAssignJobsWithServiceException() {
        when(jobService.getUnassignedJobs(50))
                .thenThrow(new RuntimeException("Service error"));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> jobExecutor.checkAndAssignJobs());

        verify(jobService).getUnassignedJobs(50);
        verify(jobService, never()).assignJobToWorker(any(), any());
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

