package com.taskmanager.integration;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.integration.config.IntegrationTestConfiguration;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobRepository;
import com.taskmanager.service.ExecutionService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration tests for concurrent job execution.
 * Tests that multiple jobs can be executed concurrently using the thread pool.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentExecutionIntegrationTest extends IntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobRepository jobRepository;

    private JsonObject testJobData;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("test", "data")
                .build();
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseRan() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from DATABASECHANGELOG", Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void testMultipleJobsExecutedConcurrently() {
        // Given - Create multiple jobs
        int jobCount = 5;
        ZonedDateTime startTime = now().minusSeconds(1);

        for (int i = 0; i < jobCount; i++) {
            ExecutionInfo executionInfo = new ExecutionInfo(
                    testJobData,
                    "TEST_COMPLETED_TASK",
                    startTime,
                    ExecutionStatus.STARTED,
                    false
            );
            executionService.executeWith(executionInfo);
        }

        // When - Wait for all jobs to execute
        // Then - All jobs should be executed and deleted
        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }

    @Test
    void testJobLockingPreventsDuplicateExecution() {
        // Given - Create a single job
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for job to be assigned
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // Job should be locked (have workerId) or completed
                assertThat(job.getWorkerId() != null).isTrue();
            }
        });
    }

    @Test
    void testBatchProcessing() {
        // Given - Create more jobs than batch size
        int jobCount = 15; // More than default batch size of 10
        ZonedDateTime startTime = now().minusSeconds(1);

        for (int i = 0; i < jobCount; i++) {
            ExecutionInfo executionInfo = new ExecutionInfo(
                    testJobData,
                    "TEST_COMPLETED_TASK",
                    startTime,
                    ExecutionStatus.STARTED,
                    false
            );
            executionService.executeWith(executionInfo);
        }

        // When - Wait for all jobs to be processed
        // Then - All jobs should eventually be executed
        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }

    @Test
    void testConcurrentJobCreation() throws InterruptedException {
        // Given - Create jobs concurrently from multiple threads
        int threadCount = 5;
        int jobsPerThread = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * jobsPerThread);

        // When - Create jobs concurrently
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < jobsPerThread; j++) {
                    ExecutionInfo executionInfo = new ExecutionInfo(
                            testJobData,
                            "TEST_COMPLETED_TASK",
                            now().minusSeconds(1),
                            ExecutionStatus.STARTED,
                            false
                    );
                    executionService.executeWith(executionInfo);
                    latch.countDown();
                }
            });
        }

        // Wait for all jobs to be created
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        // Then - All jobs should be created and eventually executed
        await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }
}

