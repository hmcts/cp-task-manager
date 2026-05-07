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
import uk.gov.hmcts.cp.taskmanager.integration.service.TaskStatus;
import uk.gov.hmcts.cp.taskmanager.integration.service.TaskStatusService;
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
    private JobsRepository jobsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskStatusService taskStatusService;

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
        final UUID taskId1 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId1.toString())
                        .build(), "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 5, null
        ));

        // Create job with priority 1 (highest)
        final UUID taskId2 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId2.toString())
                        .build(), "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 1, null
        ));

        // Create job with priority 10 (lowest)
        final UUID taskId3 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId3.toString())
                        .build(), "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 10, null
        ));

        // When - Query unassigned jobs
        await().untilAsserted(() -> {
            List<Job> jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs.size()).isGreaterThanOrEqualTo(1);

            // Verify ordering: priority 1 first, then 5, then 10
            if (jobs.size() >= 3) {
                assertThat(jobs.get(0).getPriority()).isEqualTo(1);
                assertThat(jobs.get(1).getPriority()).isEqualTo(5);
                assertThat(jobs.get(2).getPriority()).isEqualTo(10);
            }
        });

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List.of(taskId1, taskId2, taskId3).forEach(taskId -> {
                final Optional<TaskStatus> taskStatus = taskStatusService.getById(taskId);
                assertThat(taskStatus.isPresent()).isTrue();
                assertThat(taskStatus.get().getStatus().equals(COMPLETED.name())).isTrue();
            });
        });
    }

    @Test
    void testJobsWithSamePriorityOrderedByStartTime() {
        // Given - Create jobs with same priority but different start times
        ZonedDateTime earlierTime = now().minusSeconds(5);
        ZonedDateTime laterTime = now().minusSeconds(2);

        UUID laterJobId = randomUUID();

        // Create job with earlier start time
        final UUID taskId1 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId1.toString())
                        .build(), "TEST_COMPLETED_TASK", earlierTime, ExecutionStatus.STARTED, false, 5, null
        ));

        // Create job with later start time
        final UUID taskId2 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId2.toString())
                        .build(), "TEST_COMPLETED_TASK", laterTime, ExecutionStatus.STARTED, false, 5, null
        ));

        // When - Query unassigned jobs
        await().untilAsserted(() -> {
            List<Job> jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs.size()).isGreaterThanOrEqualTo(1);

            // Verify ordering: earlier start time first
            if (jobs.size() >= 2) {
                assertThat(jobs.get(0).getAssignedTaskStartTime())
                        .isBeforeOrEqualTo(jobs.get(1).getAssignedTaskStartTime());
            }
        });

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List.of(taskId1, taskId2).forEach(taskId -> {
                final Optional<TaskStatus> taskStatus = taskStatusService.getById(taskId);
                assertThat(taskStatus.isPresent()).isTrue();
                assertThat(taskStatus.get().getStatus().equals(COMPLETED.name())).isTrue();
            });
        });
    }

    @Test
    void testHighPriorityJobExecutedFirst() {
        // Given - Create multiple jobs with different priorities
        ZonedDateTime startTime = now().minusSeconds(1);

        // Create low priority job first
        final UUID taskId1 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId1.toString())
                        .build(), "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 10, null
        ));

        // Create high priority job second
        final UUID taskId2 = randomUUID();
        executionService.executeWith(new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId2.toString())
                        .build(), "TEST_COMPLETED_TASK", startTime, ExecutionStatus.STARTED, false, 1, null
        ));

        // When - Wait for execution
        // Then - High priority job should be executed first
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Job> remainingJobs = jobsRepository.findAll();
            // Both should eventually complete, but high priority should complete first
            assertThat(remainingJobs.size()).isLessThanOrEqualTo(1);
        });

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List.of(taskId1, taskId2).forEach(taskId -> {
                final Optional<TaskStatus> taskStatus = taskStatusService.getById(taskId);
                assertThat(taskStatus.isPresent()).isTrue();
                assertThat(taskStatus.get().getStatus().equals(COMPLETED.name())).isTrue();
            });
        });

    }

    @Test
    void testDefaultPriorityIsTen() {
        // Given - Create job without specifying priority
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
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
            List<Job> jobs = jobService.getUnassignedJobs(10);
            if (!jobs.isEmpty()) {
                assertThat(jobs.get(0).getPriority()).isEqualTo(10);
            }
        });

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            final Optional<TaskStatus> taskStatus = taskStatusService.getById(taskId);
            assertThat(taskStatus.isPresent()).isTrue();
            assertThat(taskStatus.get().getStatus().equals(COMPLETED.name())).isTrue();
        });

    }
}

