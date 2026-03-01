package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;
import static uk.gov.hmcts.cp.taskmanager.integration.PostgresIntegrationTestBase.ERROR_KEY;
import static uk.gov.hmcts.cp.taskmanager.integration.PostgresIntegrationTestBase.ID_KEY;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.integration.service.TaskStatusService;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;

import java.util.UUID;

import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Test task that fails to complete immediately.
 * Used for integration testing of tasks that throw unexpected exception during task execution.
 */
@Task("TEST_ERROR_TASK")
@Component
public class TestErrorTask implements ExecutableTask {

    @Autowired
    private TaskStatusService taskStatusService;

    private static final Logger logger = LoggerFactory.getLogger(TestErrorTask.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        final JsonObject jobData = executionInfo.getJobData();

        logger.info("TestErrorTask executing for job: {}", jobData);

        if (jobData.containsKey(ERROR_KEY)) {
            final UUID id = jobData.containsKey(ID_KEY) ? fromString(jobData.getString(ID_KEY)) : randomUUID();
            taskStatusService.recordRetryAttempt(id, jobData);

            throw new IllegalStateException("Task failed to complete due to unexpected errors!");
        }

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

