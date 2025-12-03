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
 * Second step in a test workflow.
 * Completes the workflow.
 */
@Task("TEST_WORKFLOW_TASK_2")
@Component
public class TestWorkflowTask2 implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestWorkflowTask2.class);
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestWorkflowTask2 executing for job: {}", executionInfo);
        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}

