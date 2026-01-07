package com.taskmanager.integration.tasks;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;

/**
 * Test task that completes immediately.
 * Used for integration testing of successful job execution.
 */
@Task("TEST_COMPLETED_TASK")
@Component
public class TestCompletedTask implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestCompletedTask.class);
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestCompletedTask executing for job: {}", executionInfo.getJobData());
        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

