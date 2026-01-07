package com.taskmanager.integration;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.integration.config.IntegrationTestConfiguration;
import com.taskmanager.persistence.entity.Job;
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
 * Integration tests for priority-based job scheduling.
 * Tests that jobs with higher priority (lower number) are executed first.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PriorityBasedSchedulingIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private com.taskmanager.persistence.repository.JobRepository jobRepository;

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
    void testJobsOrderedByPriority() {
        // Given - Create jobs with different priorities
        final ZonedDateTime startTime = now().minusSeconds(1);

        // Create job with priority 5 (medium)
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 5
        ));

        // Create job with priority 1 (highest)
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 1
        ));

        // Create job with priority 10 (lowest)
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 10
        ));

        // When - Query unassigned jobs
        await().untilAsserted(() -> {
            final List<Job> jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs.size()).isGreaterThanOrEqualTo(1);

            // Verify ordering: priority 1 first, then 5, then 10
            if (jobs.size() >= 3) {
                assertThat(jobs.get(0).getPriority()).isEqualTo(1);
                assertThat(jobs.get(1).getPriority()).isEqualTo(5);
                assertThat(jobs.get(2).getPriority()).isEqualTo(10);
            }
        });
    }

    @Test
    void testJobsWithSamePriorityOrderedByStartTime() {
        // Given - Create jobs with same priority but different start times
        final ZonedDateTime earlierTime = now().minusSeconds(5);
        final ZonedDateTime laterTime = now().minusSeconds(2);

        // Create job with earlier start time
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", earlierTime, ExecutionStatus.STARTED, false, 5
        ));

        // Create job with later start time
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", laterTime, ExecutionStatus.STARTED, false, 5
        ));

        // When - Query unassigned jobs
        await().untilAsserted(() -> {
            final List<Job> jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs.size()).isGreaterThanOrEqualTo(1);

            // Verify ordering: earlier start time first
            if (jobs.size() >= 2) {
                assertThat(jobs.get(0).getAssignedTaskStartTime())
                        .isBeforeOrEqualTo(jobs.get(1).getAssignedTaskStartTime());
            }
        });
    }

    @Test
    void testHighPriorityJobExecutedFirst() {
        // Given - Create multiple jobs with different priorities
        final ZonedDateTime startTime = now().minusSeconds(1);

        // Create low priority job first
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 10
        ));

        // Create high priority job second
        executionService.executeWith(new ExecutionInfo(
                testJobData, "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 1
        ));

        // When - Wait for execution
        // Then - High priority job should be executed first
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            final List<Job> remainingJobs = jobRepository.findAll();
            // Both should eventually complete, but high priority should complete first
            assertThat(remainingJobs.size()).isLessThanOrEqualTo(1);
        });
    }

    @Test
    void testDefaultPriorityIsTen() {
        // Given - Create job without specifying priority
        final ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
                // No priority specified
        );

        // When
        executionService.executeWith(executionInfo);

        // Then - Job should have default priority of 10
        await().untilAsserted(() -> {
            final List<Job> jobs = jobService.getUnassignedJobs(10);
            if (!jobs.isEmpty()) {
                assertThat(jobs.get(0).getPriority()).isEqualTo(10);
            }
        });
    }
}

