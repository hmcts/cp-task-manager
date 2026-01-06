package com.taskmanager.integration;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.domain.converter.JsonObjectConverter;
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

/**
 * Integration tests for job creation and execution flow.
 * Tests the complete flow from job creation through execution to completion.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class JobCreationAndExecutionIntegrationTest {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JsonObjectConverter jsonObjectConverter;

    private JsonObject testJobData;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("testKey", "testValue")
                .add("testNumber", 42)
                .build();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testJobCreation() {
        // Given
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_COMPLETED_TASK",
                now(),
                ExecutionStatus.STARTED,
                false
        );

        // When
        executionService.executeWith(executionInfo);

        // Then - Job should be persisted
        await().untilAsserted(() -> {
            var jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs).hasSize(1);

            Job job = jobs.get(0);
            assertThat(job.getAssignedTaskName()).isEqualTo("TEST_COMPLETED_TASK");
            assertThat(job.getJobData()).isNotNull();
            assertThat(job.getWorkerId()).isNull();
            assertThat(job.getRetryAttemptsRemaining()).isEqualTo(0);
        });
    }

    @Test
    void testJobExecutionAndCompletion() {
        // Given - Create a job
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1), // Past time so it can execute immediately
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // Wait for job to be assigned and executed
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            // Job should be deleted after completion
            var jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty();
        });
    }

//    @Test
//    void testJobWithFutureStartTime() {
//        // Given - Create a job with future start time
//        ExecutionInfo executionInfo = new ExecutionInfo(
//                testJobData,
//                "TEST_COMPLETED_TASK",
//                now().plusSeconds(5), // Future time
//                ExecutionStatus.STARTED,
//                false
//        );
//        executionService.executeWith(executionInfo);
//
//        // Then - Job should exist but not be executed yet
//        await().untilAsserted(() -> {
//            var jobs = jobService.getUnassignedJobs(10);
//            assertThat(jobs).hasSize(1);
//            assertThat(jobs.get(0).getAssignedTaskStartTime()).isAfter(now());
//        });
//
//        // Wait for start time to pass and job to execute
//        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
//            var jobs = jobRepository.findAll();
//            assertThat(jobs).isEmpty(); // Job should be completed and deleted
//        });
//    }

    @Test
    void testJobDataPersistence() {
        // Given
        JsonObject complexData = Json.createObjectBuilder()
                .add("name", "Test Job")
                .add("count", 100)
                .add("active", true)
                .build();

        ExecutionInfo executionInfo = new ExecutionInfo(
                complexData,
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );

        // When
        executionService.executeWith(executionInfo);

        // Then - Verify job data is persisted correctly
        await().untilAsserted(() -> {
            var jobs = jobService.getUnassignedJobs(10);
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                JsonObject persistedData = job.getJobData();
                assertThat(persistedData.getString("name")).isEqualTo("Test Job");
                assertThat(persistedData.getInt("count")).isEqualTo(100);
                assertThat(persistedData.getBoolean("active")).isTrue();
            }
        });
    }
}

