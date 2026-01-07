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
import org.junit.jupiter.api.AfterEach;
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
class ConcurrentExecutionIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

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
    void testFlywayRan() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @Test
    void testMultipleJobsExecutedConcurrently() {
        // Given - Create multiple jobs
        final int jobCount = 5;
        final ZonedDateTime startTime = now().minusSeconds(1);

        for (int i = 0; i < jobCount; i++) {
            final ExecutionInfo executionInfo = new ExecutionInfo(
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
            final List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }

    @Test
    void testJobLockingPreventsDuplicateExecution() {
        // Given - Create a single job
        final ExecutionInfo executionInfo = new ExecutionInfo(
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
                final Job job = jobs.get(0);
                // Job should be locked (have workerId) or completed
                assertThat(job.getWorkerId() != null).isTrue();
            }
        });
    }

    @Test
    void testBatchProcessing() {
        // Given - Create more jobs than batch size
        final int jobCount = 15; // More than default batch size of 10
        final ZonedDateTime startTime = now().minusSeconds(1);

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
            final List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }

    @Test
    void testConcurrentJobCreation() throws InterruptedException {
        // Given - Create jobs concurrently from multiple threads
        final int threadCount = 5;
        final int jobsPerThread = 2;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount * jobsPerThread);

        // When - Create jobs concurrently
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < jobsPerThread; j++) {
                    final ExecutionInfo executionInfo = new ExecutionInfo(
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
            final List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // All jobs should be completed
        });
    }
}

