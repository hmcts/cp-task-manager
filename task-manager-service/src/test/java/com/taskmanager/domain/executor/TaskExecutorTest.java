package com.taskmanager.domain.executor;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.service.JobService;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.TaskRegistry;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskExecutorTest {

    @Mock
    private JobService jobService;

    @Mock
    private TaskRegistry taskRegistry;

    @Mock
    private ExecutableTask executableTask;
    
    private PlatformTransactionManager transactionManager;

    private Job testJob;
    private JsonObject testJobData;
    private TaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        testJob = new Job(
                UUID.randomUUID(),
                testJobData,
                "TEST_TASK",
                ZonedDateTime.now().minusMinutes(1), // Past time
                UUID.randomUUID(),
                ZonedDateTime.now(),
                5,
                10
        );

        // Create a simple transaction manager that actually executes transactions
        // Using a real implementation that will execute TransactionTemplate callbacks
        PlatformTransactionManager realTransactionManager = new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new SimpleTransactionStatus();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                // No-op - transactions are always active
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                // No-op
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
                // No-op
            }
            
            @Override
            protected boolean isExistingTransaction(Object transaction) {
                return false;
            }
        };

        taskExecutor = new TaskExecutor(testJob, taskRegistry, jobService, realTransactionManager);
    }

    @Test
    void testRunWithTaskNotFound() {
        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.empty());

        taskExecutor.run();

        verify(taskRegistry).getTask("TEST_TASK");
        verify(jobService).releaseJob(testJob.getJobId());
        verify(executableTask, never()).execute(any());
    }

    @Test
    void testRunWithTaskStartTimeNotReached() {
        Job futureJob = new Job(
                UUID.randomUUID(),
                testJobData,
                "FUTURE_TASK",
                ZonedDateTime.now().plusHours(1), // Future time
                UUID.randomUUID(),
                ZonedDateTime.now(),
                5,
                10
        );
        TaskExecutor futureTaskExecutor = new TaskExecutor(futureJob, taskRegistry, jobService, transactionManager);

        when(taskRegistry.getTask("FUTURE_TASK")).thenReturn(Optional.of(executableTask));

        futureTaskExecutor.run();

        verify(taskRegistry).getTask("FUTURE_TASK");
        verify(jobService).releaseJob(futureJob.getJobId());
        verify(executableTask, never()).execute(any());
    }

    @Test
    void testRunWithCompletedStatus() {
        ExecutionInfo completedInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("TEST_TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.COMPLETED)
                .build();

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(completedInfo);

        taskExecutor.run();

        verify(taskRegistry).getTask("TEST_TASK");
        verify(executableTask).execute(any(ExecutionInfo.class));
        verify(jobService).deleteJob(testJob.getJobId());
        verify(jobService, never()).releaseJob(any());
    }

    @Test
    void testRunWithInProgressStatus() {
        ExecutionInfo inProgressInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("NEXT_TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .build();

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(taskRegistry.findRetryAttemptsRemainingFor("NEXT_TASK")).thenReturn(3);
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(inProgressInfo);

        taskExecutor.run();

        verify(taskRegistry).getTask("TEST_TASK");
        verify(executableTask).execute(any(ExecutionInfo.class));
        verify(jobService).updateJobTaskData(eq(testJob.getJobId()), any());
        verify(jobService).updateNextTaskDetails(eq(testJob.getJobId()), eq("NEXT_TASK"), any(), eq(3));
        verify(jobService).releaseJob(testJob.getJobId());
    }

    @Test
    void testRunWithRetry() {
        List<Long> retryDurations = Arrays.asList(10L, 20L, 30L);
        // retryAttemptsRemaining should match the number of retry durations (3)
        testJob.setRetryAttemptsRemaining(3);
        
        ExecutionInfo retryInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("TEST_TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .withShouldRetry(true)
                .build();

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(retryInfo);
        when(executableTask.getRetryDurationsInSecs()).thenReturn(Optional.of(retryDurations));

        taskExecutor.run();

        verify(taskRegistry).getTask("TEST_TASK");
        verify(executableTask).execute(any(ExecutionInfo.class));
        // getRetryDurationsInSecs() is called in canRetry() and performRetry()
        verify(executableTask, atLeastOnce()).getRetryDurationsInSecs();
        // retryAttemptsRemaining starts at 3, so after decrement it should be 2
        // Index calculation: retryDurations.get(3 - 3) = retryDurations.get(0) = 10L
        verify(jobService, atLeastOnce()).updateNextTaskRetryDetails(eq(testJob.getJobId()), any(ZonedDateTime.class), eq(2));
        verify(jobService, atLeastOnce()).releaseJob(testJob.getJobId());
    }

    @Test
    void testRunWithRetryButNoRetryDurations() {
        ExecutionInfo retryInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("NEXT_TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .withShouldRetry(true)
                .build();

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(taskRegistry.findRetryAttemptsRemainingFor("NEXT_TASK")).thenReturn(2);
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(retryInfo);
        when(executableTask.getRetryDurationsInSecs()).thenReturn(Optional.empty());

        taskExecutor.run();

        verify(executableTask).execute(any(ExecutionInfo.class));
        verify(jobService, never()).updateNextTaskRetryDetails(any(), any(), any());
        verify(jobService).updateNextTaskDetails(any(), any(), any(), any());
        verify(jobService).releaseJob(testJob.getJobId());
    }

    @Test
    void testRunWithRetryButZeroAttemptsRemaining() {
        testJob.setRetryAttemptsRemaining(0);
        ExecutionInfo retryInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("TEST_TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .withShouldRetry(true)
                .build();

        List<Long> retryDurations = Arrays.asList(10L, 20L);

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(retryInfo);
        when(executableTask.getRetryDurationsInSecs()).thenReturn(Optional.of(retryDurations));

        taskExecutor.run();

        verify(executableTask).execute(any(ExecutionInfo.class));
        verify(jobService, never()).updateNextTaskRetryDetails(any(), any(), any());
        verify(jobService).updateNextTaskDetails(any(), any(), any(), any());
    }

    @Test
    void testRunWithException() {
        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenThrow(new RuntimeException("Task execution failed"));

        taskExecutor.run();

        verify(jobService).releaseJob(testJob.getJobId());
    }

    @Test
    void testRunWithExceptionOnRelease() {
        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenThrow(new RuntimeException("Task execution failed"));
        doThrow(new RuntimeException("Release failed")).when(jobService).releaseJob(any(UUID.class));

        // Should not throw exception, should handle gracefully
        assertDoesNotThrow(() -> taskExecutor.run());

        verify(jobService).releaseJob(testJob.getJobId());
    }

    @Test
    void testToString() {
        String result = taskExecutor.toString();
        assertNotNull(result);
        assertTrue(result.contains("JobExecutor"));
        assertTrue(result.contains("job="));
    }

    @Test
    void testRetryWithSameTask() {
        ExecutionInfo retryInfo = ExecutionInfo.executionInfo()
                .withJobData(testJobData)
                .withAssignedTaskName("TEST_TASK") // Same task
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(ExecutionStatus.INPROGRESS)
                .withShouldRetry(false)
                .build();

        when(taskRegistry.getTask("TEST_TASK")).thenReturn(Optional.of(executableTask));
        when(executableTask.execute(any(ExecutionInfo.class))).thenReturn(retryInfo);

        taskExecutor.run();

        verify(jobService).updateNextTaskDetails(eq(testJob.getJobId()), eq("TEST_TASK"), any(), eq(5));
    }
}

