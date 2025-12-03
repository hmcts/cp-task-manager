package com.taskmanager.service;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.service.JobService;
import com.taskmanager.service.task.TaskRegistry;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private JobService jobService;

    @Mock
    private TaskRegistry taskRegistry;

    @InjectMocks
    private ExecutionService executionService;

    private ExecutionInfo testExecutionInfo;
    private JsonObject testJobData;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        testExecutionInfo = new ExecutionInfo(
                testJobData,
                "TEST_TASK",
                ZonedDateTime.now(),
                ExecutionStatus.STARTED,
                false,
                5
        );
    }

    @Test
    void testExecuteWith() {
        int retryAttempts = 3;
        when(taskRegistry.findRetryAttemptsRemainingFor("TEST_TASK")).thenReturn(retryAttempts);

        executionService.executeWith(testExecutionInfo);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobService).insertJob(jobCaptor.capture());

        Job capturedJob = jobCaptor.getValue();
        assertNotNull(capturedJob.getJobId());
        assertEquals(testJobData, capturedJob.getJobData());
        assertEquals("TEST_TASK", capturedJob.getAssignedTaskName());
        assertEquals(testExecutionInfo.getAssignedTaskStartTime(), capturedJob.getAssignedTaskStartTime());
        assertEquals(retryAttempts, capturedJob.getRetryAttemptsRemaining());
        assertEquals(5, capturedJob.getPriority());
        verify(taskRegistry).findRetryAttemptsRemainingFor("TEST_TASK");
    }

    @Test
    void testExecuteWithNullPriority() {
        ExecutionInfo infoWithoutPriority = new ExecutionInfo(
                testJobData,
                "TEST_TASK",
                ZonedDateTime.now(),
                ExecutionStatus.STARTED
        );
        when(taskRegistry.findRetryAttemptsRemainingFor("TEST_TASK")).thenReturn(2);

        executionService.executeWith(infoWithoutPriority);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobService).insertJob(jobCaptor.capture());

        Job capturedJob = jobCaptor.getValue();
        assertEquals(10, capturedJob.getPriority()); // Default priority
    }

    @Test
    void testExecuteWithZeroRetryAttempts() {
        when(taskRegistry.findRetryAttemptsRemainingFor("TEST_TASK")).thenReturn(0);

        executionService.executeWith(testExecutionInfo);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobService).insertJob(jobCaptor.capture());

        Job capturedJob = jobCaptor.getValue();
        assertEquals(0, capturedJob.getRetryAttemptsRemaining());
    }

    @Test
    void testExecuteWithHighPriority() {
        ExecutionInfo highPriorityInfo = new ExecutionInfo(
                testJobData,
                "HIGH_PRIORITY_TASK",
                ZonedDateTime.now(),
                ExecutionStatus.STARTED,
                false,
                1  // Highest priority
        );
        when(taskRegistry.findRetryAttemptsRemainingFor("HIGH_PRIORITY_TASK")).thenReturn(5);

        executionService.executeWith(highPriorityInfo);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobService).insertJob(jobCaptor.capture());

        Job capturedJob = jobCaptor.getValue();
        assertEquals(1, capturedJob.getPriority());
    }

    @Test
    void testExecuteWithLowPriority() {
        ExecutionInfo lowPriorityInfo = new ExecutionInfo(
                testJobData,
                "LOW_PRIORITY_TASK",
                ZonedDateTime.now(),
                ExecutionStatus.STARTED,
                false,
                10  // Lowest priority
        );
        when(taskRegistry.findRetryAttemptsRemainingFor("LOW_PRIORITY_TASK")).thenReturn(1);

        executionService.executeWith(lowPriorityInfo);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobService).insertJob(jobCaptor.capture());

        Job capturedJob = jobCaptor.getValue();
        assertEquals(10, capturedJob.getPriority());
    }

    @Test
    void testExecuteWithShouldRetry() {
        ExecutionInfo retryInfo = new ExecutionInfo(
                testJobData,
                "RETRY_TASK",
                ZonedDateTime.now(),
                ExecutionStatus.STARTED,
                true,  // shouldRetry
                3
        );
        when(taskRegistry.findRetryAttemptsRemainingFor("RETRY_TASK")).thenReturn(3);

        executionService.executeWith(retryInfo);

        verify(jobService).insertJob(any(Job.class));
    }
}

