package com.taskmanager.persistence.service;

import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobRepository;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JsonObjectConverter jsonObjectConverter;

    @InjectMocks
    private JobService jobService;

    private Job testJob;
    private UUID testJobId;
    private UUID testWorkerId;
    private JsonObject testJobData;

    @BeforeEach
    void setUp() {
        testJobId = UUID.randomUUID();
        testWorkerId = UUID.randomUUID();
        testJobData = Json.createObjectBuilder()
                .add("key", "value")
                .build();

        testJob = new Job(testJobId, testJobData, "TEST_TASK", 
                ZonedDateTime.now(), null, null, 5, 10);
    }

    @Test
    void testGetUnassignedJobs() {
        List<Job> expectedJobs = Arrays.asList(testJob);
        when(jobRepository.findUnassignedJobs(any(ZonedDateTime.class)))
                .thenReturn(expectedJobs);

        List<Job> result = jobService.getUnassignedJobs();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testJob, result.get(0));
        verify(jobRepository).findUnassignedJobs(any(ZonedDateTime.class));
    }

    @Test
    void testGetUnassignedJobsWithBatchSize() {
        int batchSize = 10;
        List<Job> expectedJobs = Arrays.asList(testJob);
        when(jobRepository.findUnassignedJobsWithLimit(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(expectedJobs);

        List<Job> result = jobService.getUnassignedJobs(batchSize);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(jobRepository).findUnassignedJobsWithLimit(any(ZonedDateTime.class), 
                eq(PageRequest.of(0, batchSize)));
    }

    @Test
    void testAssignJobToWorker() {
        when(jobRepository.findByJobId(testJobId)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenReturn(testJob);

        Job result = jobService.assignJobToWorker(testJobId, testWorkerId);

        assertNotNull(result);
        verify(jobRepository).findByJobId(testJobId);
        verify(jobRepository).save(any(Job.class));
        assertEquals(testWorkerId, testJob.getWorkerId());
        assertNotNull(testJob.getWorkerLockTime());
    }

    @Test
    void testAssignJobToWorkerJobNotFound() {
        when(jobRepository.findByJobId(testJobId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
                () -> jobService.assignJobToWorker(testJobId, testWorkerId));
        verify(jobRepository, never()).save(any(Job.class));
    }

    @Test
    void testDecrementRetryAttempts() {
        testJob.setRetryAttemptsRemaining(5);
        when(jobRepository.findByJobId(testJobId)).thenReturn(Optional.of(testJob));
        when(jobRepository.save(any(Job.class))).thenReturn(testJob);

        jobService.decrementRetryAttempts(testJobId);

        verify(jobRepository).findByJobId(testJobId);
        verify(jobRepository).save(testJob);
        assertEquals(4, testJob.getRetryAttemptsRemaining());
    }

    @Test
    void testDecrementRetryAttemptsWhenZero() {
        testJob.setRetryAttemptsRemaining(0);
        when(jobRepository.findByJobId(testJobId)).thenReturn(Optional.of(testJob));

        jobService.decrementRetryAttempts(testJobId);

        verify(jobRepository).findByJobId(testJobId);
        verify(jobRepository, never()).save(any(Job.class));
        assertEquals(0, testJob.getRetryAttemptsRemaining());
    }

    @Test
    void testDecrementRetryAttemptsJobNotFound() {
        when(jobRepository.findByJobId(testJobId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, 
                () -> jobService.decrementRetryAttempts(testJobId));
    }

    @Test
    void testInsertJob() {
        jobService.insertJob(testJob);

        verify(jobRepository).save(testJob);
    }

    @Test
    void testUpdateJobTaskData() {
        String jsonString = "{\"key\":\"value\"}";
        when(jsonObjectConverter.convertToDatabaseColumn(testJobData)).thenReturn(jsonString);

        jobService.updateJobTaskData(testJobId, testJobData);

        verify(jsonObjectConverter).convertToDatabaseColumn(testJobData);
        verify(jobRepository).updateJobData(jsonString, testJobId);
    }

    @Test
    void testUpdateNextTaskDetails() {
        String assignedTaskName = "NEW_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        Integer retryAttempts = 3;

        jobService.updateNextTaskDetails(testJobId, assignedTaskName, startTime, retryAttempts);

        verify(jobRepository).updateNextTaskDetails(assignedTaskName, startTime, retryAttempts, testJobId);
    }

    @Test
    void testDeleteJob() {
        jobService.deleteJob(testJobId);

        verify(jobRepository).deleteJob(testJobId);
    }

    @Test
    void testReleaseJob() {
        jobService.releaseJob(testJobId);

        verify(jobRepository).releaseJob(testJobId);
    }

    @Test
    void testUpdateNextTaskRetryDetails() {
        ZonedDateTime startTime = ZonedDateTime.now();
        Integer retryAttempts = 2;

        jobService.updateNextTaskRetryDetails(testJobId, startTime, retryAttempts);

        verify(jobRepository).updateNextTaskRetryDetails(startTime, retryAttempts, testJobId);
    }
}

