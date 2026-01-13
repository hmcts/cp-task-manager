package uk.gov.hmcts.cp.taskmanager.integration;

import static jakarta.json.Json.createObjectBuilder;
import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatus;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatusRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for job creation and execution flow.
 * Tests the complete flow from job creation through execution to completion.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"job.executor.poll-interval=3000"})
class JobCreationAndExecutionIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobsRepository jobsRepository;

    @Autowired
    private JsonObjectConverter jsonObjectConverter;

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
    void testJobCreation() {
        // Given
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
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

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }

    @Test
    void testJobExecutionAndCompletion() {
        // Given - Create a job
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1), // Past time so it can execute immediately
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // Wait for job to be assigned and executed
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            // Job should be deleted after completion
            var jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty();
        });

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }

    @Test
    void testJobWithFutureStartTime() {
        // Given - Create a job with future start time
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_COMPLETED_TASK",
                now().plusSeconds(8), // Future time
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // Then - Job should exist but not be executed yet
        await().untilAsserted(() -> {
            var jobs = jobService.getUnassignedJobs(10);
            assertThat(jobs).hasSize(1);
        });

        // Wait for start time to pass and job to execute
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            var jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty(); // Job should be completed and deleted
        });
    }

    @Test
    void testJobDataPersistence() {
        // Given
        final UUID taskId = randomUUID();
        final JsonObject complexData = createObjectBuilder()
                .add("name", "Test Job")
                .add(ID_KEY, taskId.toString())
                .add("count", 100)
                .add("active", true)
                .build();

        final ExecutionInfo executionInfo = new ExecutionInfo(
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

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusRepository.findById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }
}

