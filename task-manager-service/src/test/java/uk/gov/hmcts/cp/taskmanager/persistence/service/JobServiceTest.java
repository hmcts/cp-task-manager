package uk.gov.hmcts.cp.taskmanager.persistence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobsRepository jobsRepository;

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
    void testGetUnassignedJobsWithBatchSize() {
        int batchSize = 10;
        List<Job> expectedJobs = Arrays.asList(testJob);
        when(jobsRepository.findUnassignedJobsWithLimit(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(expectedJobs);

        List<Job> result = jobService.getUnassignedJobs(batchSize);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(jobsRepository).findUnassignedJobsWithLimit(any(ZonedDateTime.class),
                eq(PageRequest.of(0, batchSize)));
    }

    @Test
    void testDecrementRetryAttempts() {
        testJob.setRetryAttemptsRemaining(5);
        when(jobsRepository.findByJobId(testJobId)).thenReturn(Optional.of(testJob));
        when(jobsRepository.save(any(Job.class))).thenReturn(testJob);

        jobService.decrementRetryAttempts(testJobId);

        verify(jobsRepository).findByJobId(testJobId);
        verify(jobsRepository).save(testJob);
        assertEquals(4, testJob.getRetryAttemptsRemaining());
    }

    @Test
    void testDecrementRetryAttemptsWhenZero() {
        testJob.setRetryAttemptsRemaining(0);
        when(jobsRepository.findByJobId(testJobId)).thenReturn(Optional.of(testJob));

        jobService.decrementRetryAttempts(testJobId);

        verify(jobsRepository).findByJobId(testJobId);
        verify(jobsRepository, never()).save(any(Job.class));
        assertEquals(0, testJob.getRetryAttemptsRemaining());
    }

    @Test
    void testDecrementRetryAttemptsJobNotFound() {
        when(jobsRepository.findByJobId(testJobId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> jobService.decrementRetryAttempts(testJobId));
    }

    @Test
    void testInsertJob() {
        jobService.insertJob(testJob);

        verify(jobsRepository).save(testJob);
    }

    @Test
    void testUpdateJobTaskData() {
        String jsonString = "{\"key\":\"value\"}";
        when(jsonObjectConverter.convertToDatabaseColumn(testJobData)).thenReturn(jsonString);

        jobService.updateJobTaskData(testJobId, testJobData);

        verify(jsonObjectConverter).convertToDatabaseColumn(testJobData);
        verify(jobsRepository).updateJobData(jsonString, testJobId);
    }

    @Test
    void testUpdateNextTaskDetails() {
        String assignedTaskName = "NEW_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        Integer retryAttempts = 3;

        jobService.updateNextTaskDetails(testJobId, assignedTaskName, startTime, retryAttempts);

        verify(jobsRepository).updateNextTaskDetails(assignedTaskName, startTime, retryAttempts, testJobId);
    }

    @Test
    void testDeleteJob() {
        jobService.deleteJob(testJobId);

        verify(jobsRepository).deleteJob(testJobId);
    }

    @Test
    void testReleaseJob() {
        jobService.releaseJob(testJobId);

        verify(jobsRepository).releaseJob(testJobId);
    }

    @Test
    void testUpdateNextTaskRetryDetails() {
        ZonedDateTime startTime = ZonedDateTime.now();
        Integer retryAttempts = 2;

        jobService.updateNextTaskRetryDetails(testJobId, startTime, retryAttempts);

        verify(jobsRepository).updateNextTaskRetryDetails(startTime, retryAttempts, testJobId);
    }
}

