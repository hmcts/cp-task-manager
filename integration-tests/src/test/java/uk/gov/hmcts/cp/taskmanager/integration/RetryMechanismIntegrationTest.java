package uk.gov.hmcts.cp.taskmanager.integration;

import static jakarta.json.Json.createObjectBuilder;
import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatus;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatusRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration tests for retry mechanism.
 * Tests that tasks with retry configuration are retried with exponential backoff.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetryMechanismIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobsRepository jobsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskStatusRepository taskStatusRepository;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testRetryTaskDecrementsAttempts() {
        // Given - Create a job with retry task
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
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
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        ZonedDateTime initialStartTime = now();

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // Start time should be updated for retry (delayed)
                assertThat(job.getAssignedTaskStartTime()).isAfter(initialStartTime);
            }
        });

        final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
        assertThat(task.isEmpty()).isFalse();
        assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
    }

    @Test
    void testRetryExhausted() {
        // Given - Create a job with retry task that will exhaust retries
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for all retries to be exhausted
        // Then - Job should eventually be deleted or remain with 0 retries
        await().atMost(java.time.Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // After all retries, attempts should be 0 or job deleted
                assertThat(job.getRetryAttemptsRemaining()).isEqualTo(0);
            }
        });

        final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
        assertThat(task.isEmpty()).isFalse();
        assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
    }

    @Test
    void testRetryTaskWithNoRetryConfiguration() {
        // Given - Create a job with a task that doesn't have retry configuration
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_COMPLETED_TASK", // This task doesn't have retry configuration
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for execution
        // Then - Job should complete without retries
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty(); // Job should be completed and deleted
        });

        final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
        assertThat(task.isEmpty()).isFalse();
        assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
    }
}

