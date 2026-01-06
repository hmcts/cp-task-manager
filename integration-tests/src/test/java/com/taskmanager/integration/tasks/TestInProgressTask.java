package com.taskmanager.integration.tasks;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.INPROGRESS;

/**
 * Test task that returns INPROGRESS status.
 * Used for integration testing of workflow continuation.
 */
@Task("TEST_INPROGRESS_TASK")
@Component
public class TestInProgressTask implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestInProgressTask.class);
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestInProgressTask executing for job: {}", executionInfo);
        return executionInfo().from(executionInfo)
                .withExecutionStatus(INPROGRESS)
                .build();
    }
}

