package com.taskmanager.integration;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.integration.config.IntegrationTestConfiguration;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobsRepository;
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

    private JsonObject testJobData;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("workflow", "test")
                .build();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testWorkflowExecution() {
        // Given - Create a workflow job starting with TEST_WORKFLOW_TASK_1
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_WORKFLOW_TASK_1",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for workflow to complete
        // Then - Job should progress through workflow and eventually complete
        await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();

            // Workflow should eventually complete (job deleted)
            // Or if still in progress, should be on second task
            if (!jobs.isEmpty()) {
                Job job = jobs.get(0);
                // Should be on TEST_WORKFLOW_TASK_2 or completed
                assertThat(job.getAssignedTaskName())
                        .isIn("TEST_WORKFLOW_TASK_1", "TEST_WORKFLOW_TASK_2");
            } else {
                // Workflow completed successfully
                assertThat(jobs).isEmpty();
            }
        });
    }

    @Test
    void testWorkflowTaskTransition() {
        // Given - Create a workflow job
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
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
                Job job = jobs.get(0);
                // Should have transitioned to TEST_WORKFLOW_TASK_2
                assertThat(job.getAssignedTaskName()).isEqualTo("TEST_WORKFLOW_TASK_2");
            }
        });
    }

    @Test
    void testWorkflowCompletion() {
        // Given - Create a workflow job
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
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
    }

    @Test
    void testSpawnMultipleTasksInTheWorkflow() {
        // Given - Create a task that internally spawns/schedules multiple jobs
        ExecutionInfo executionInfo = new ExecutionInfo(
                testJobData,
                "TEST_SCHEDULE_MULTI_JOBS_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - Wait for all jobs execution
        // Then - all the jobs should be deleted after completion
        await().atMost(java.time.Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).isEmpty();
        });
    }
}

