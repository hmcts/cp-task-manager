package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import static java.time.OffsetDateTime.now;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;
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
 * First step in a test workflow.
 * Returns INPROGRESS to continue to next task.
 */
@Task("TEST_WORKFLOW_TASK_1")
@Component
public class TestWorkflowTask1 implements ExecutableTask {

    @Autowired
    private TaskStatusService taskStatusService;

    private static final Logger logger = LoggerFactory.getLogger(TestWorkflowTask1.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();

        logger.info("TestWorkflowTask1 executing for job: {}", jobData);

        final UUID id = jobData.containsKey(ID_KEY) ? fromString(jobData.getString(ID_KEY)) : randomUUID();
        taskStatusService.insertTaskStatus(new TaskStatus(id, jobData, INPROGRESS.name(), now()));

        return executionInfo().from(executionInfo)
                .withAssignedTaskName("TEST_WORKFLOW_TASK_2")
                .withExecutionStatus(INPROGRESS)
                .build();
    }
}

