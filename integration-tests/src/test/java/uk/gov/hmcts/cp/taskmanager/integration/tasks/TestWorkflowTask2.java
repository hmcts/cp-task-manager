package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import static java.time.OffsetDateTime.now;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;
import static uk.gov.hmcts.cp.taskmanager.integration.PostgresIntegrationTestBase.ID_KEY;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatus;
import uk.gov.hmcts.cp.taskmanager.integration.persistence.TaskStatusService;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.util.UUID;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Second step in a test workflow.
 * Completes the workflow.
 */
@Task("TEST_WORKFLOW_TASK_2")
@Component
public class TestWorkflowTask2 implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(TestWorkflowTask2.class);

    @Autowired
    private TaskStatusService taskStatusService;

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();
        logger.info("TestWorkflowTask2 executing for job: {}", jobData);

        final UUID id = jobData.containsKey(ID_KEY) ? fromString(jobData.getString(ID_KEY)) : randomUUID();
        taskStatusService.insertTaskStatus(new TaskStatus(id, jobData, COMPLETED.name(), now()));

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

