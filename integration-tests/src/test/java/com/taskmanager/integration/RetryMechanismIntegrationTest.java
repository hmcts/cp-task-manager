package com.taskmanager.integration;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.integration.config.IntegrationTestConfiguration;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobRepository;
import com.taskmanager.persistence.service.JobService;
import com.taskmanager.service.ExecutionService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for retry mechanism.
 * Tests that tasks with retry configuration are retried with exponential backoff.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetryMechanismIntegrationTest {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    private JsonObject testJobData;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("test", "data")
                .build();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testRetryTaskDecrementsAttempts() {
        // Given - Create a job with retry task
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            // Job should still exist (not deleted) because it's retrying
            assertThat(jobs).isNotEmpty();
            
            Job job = jobs.get(0);
            // Retry attempts should be decremented (starts with 3, should be 2 after first retry)
            assertThat(job.getRetryAttemptsRemaining()).isLessThan(3);
        });
    }

    @Test
    void testRetryScheduledWithDelay() {
        // Given - Create a job with retry task
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        ZonedDateTime initialStartTime = now();

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // Start time should be updated for retry (delayed)
                assertThat(job.getAssignedTaskStartTime()).isAfter(initialStartTime);
            }
        });
    }

    @Test
    void testRetryExhausted() {
        // Given - Create a job with retry task that will exhaust retries
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for all retries to be exhausted
        // Then - Job should eventually be deleted or remain with 0 retries
        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // After all retries, attempts should be 0 or job deleted
                assertThat(job.getRetryAttemptsRemaining()).isEqualTo(0);
            }
        });
    }

    @Test
    void testRetryTaskWithNoRetryConfiguration() {
        // Given - Create a job with a task that doesn't have retry configuration
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_COMPLETED_TASK", // This task doesn't have retry configuration
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for execution
        // Then - Job should complete without retries
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // Job should be completed and deleted
        });
    }
}

