package com.taskmanager.integration.tasks;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;

import java.time.ZonedDateTime;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.domain.ScheduleJobEvent;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Second step in a test workflow.
 * Completes the workflow.
 */
@Task("TEST_SCHEDULE_MULTI_JOBS_TASK")
@Component
public class TestSpawnMultipleJobsTask implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestSpawnMultipleJobsTask.class);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestSpawnMultipleJobsTask executing for job: {}", executionInfo);

        for (int i = 0; i < 10; i++) {
            final JsonObject jobData = Json.createObjectBuilder()
                    .add("workflow", "test")
                    .add("item", i)
                    .build();
            ExecutionInfo newTask = new ExecutionInfo(
                    jobData,
                    "TEST_COMPLETED_TASK",
                    ZonedDateTime.now(),
                    ExecutionStatus.STARTED,
                    false
            );

            eventPublisher.publishEvent(new ScheduleJobEvent(newTask));
        }

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

