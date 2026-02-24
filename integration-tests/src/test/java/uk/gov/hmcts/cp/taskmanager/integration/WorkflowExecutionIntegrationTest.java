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
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration tests for workflow execution.
 * Tests multi-step workflows where tasks continue to next tasks.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowExecutionIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

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
    void testWorkflowExecution() {
        // Given - Create a workflow job starting with TEST_WORKFLOW_TASK_1
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_WORKFLOW_TASK_1",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for workflow to complete
        // Then - Job should progress through workflow and eventually complete
        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            final List<Job> jobs = jobsRepository.findAll();

            // Workflow should eventually complete (job deleted)
            // Or if still in progress, should be on second task
            if (!jobs.isEmpty()) {
                final Job job = jobs.get(0);
                assertThat(job.getWorkerId()).isNotNull();
                // Should be on TEST_WORKFLOW_TASK_2 or completed
                assertThat(job.getAssignedTaskName()).isIn("TEST_WORKFLOW_TASK_1", "TEST_WORKFLOW_TASK_2");
            } else {
                // Workflow completed successfully
                assertThat(jobs).isEmpty();
            }
        });

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusService.getById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });

        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            final List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty();
        });
    }

    @Test
    void testWorkflowTaskTransition() {
        // Given - Create a workflow job
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_WORKFLOW_TASK_1",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for first task to execute
        // Then - Job should transition to second task
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            if (!jobs.isEmpty()) {
                final Job job = jobs.get(0);
                assertThat(job.getWorkerId()).isNotNull();
                // Should have transitioned to TEST_WORKFLOW_TASK_2
                assertThat(job.getAssignedTaskName()).isEqualTo("TEST_WORKFLOW_TASK_2");
            }
        });

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusService.getById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }

    @Test
    void testWorkflowCompletion() {
        // Given - Create a workflow job
        final UUID taskId = randomUUID();
        final ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_WORKFLOW_TASK_1",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for complete workflow execution
        // Then - Job should be deleted after completion
        await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty(); // Workflow completed, job deleted
        });

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusService.getById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }

    @Test
    void testSpawnMultipleTasksInTheWorkflow() {
        // Given - Create a task that internally spawns/schedules multiple jobs
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_SCHEDULE_MULTI_JOBS_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for all jobs execution
        // Then - all the jobs should be deleted after completion
        await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() -> {
            final List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty();
        });

        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusService.getById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.stream().allMatch(t -> COMPLETED.name().equals(t.getStatus()))).isTrue();
        });
    }
}

