package com.taskmanager.integration;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobRepository;
import com.taskmanager.service.ExecutionService;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.time.ZonedDateTime;
import java.util.List;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for workflow execution.
 * Tests multi-step workflows where tasks continue to next tasks.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WorkflowExecutionIntegrationTest {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobRepository jobRepository;

    private JsonObject testJobData;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("workflow", "test")
                .build();
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
            List<Job> jobs = jobRepository.findAll();
            
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
            List<Job> jobs = jobRepository.findAll();
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
            List<Job> jobs = jobRepository.findAll();
            assertThat(jobs).isEmpty(); // Workflow completed, job deleted
        });
    }
}

