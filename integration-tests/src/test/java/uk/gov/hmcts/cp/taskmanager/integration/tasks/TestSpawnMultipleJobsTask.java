package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import java.time.ZonedDateTime;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Second step in a test workflow. Completes the workflow.
 */
@Task("TEST_SCHEDULE_MULTI_JOBS_TASK")
@Component
public class TestSpawnMultipleJobsTask implements ExecutableTask {

    private static final Logger logger = LoggerFactory.getLogger(TestSpawnMultipleJobsTask.class);

    @Autowired
    private ExecutionService executionService;

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestSpawnMultipleJobsTask executing for job: {}", executionInfo);

        for (int i = 0; i < 10; i++) {
            final JsonObject jobData = Json.createObjectBuilder()
                    .add("workflow", "test")
                    .add("item", i)
                    .build();
            ExecutionInfo childTask = new ExecutionInfo(
                    jobData,
                    "TEST_COMPLETED_TASK",
                    ZonedDateTime.now(),
                    ExecutionStatus.STARTED,
                    false
            );

            executionService.executeWith(childTask);
        }

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

