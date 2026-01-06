package com.taskmanager.integration.tasks;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.INPROGRESS;

/**
 * Test task that simulates retry behavior.
 * Returns INPROGRESS with shouldRetry=true and provides retry durations.
 * Used for integration testing of retry mechanism.
 */
@Task("TEST_RETRY_TASK")
@Component
public class TestRetryTask implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestRetryTask.class);
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestRetryTask executing for job: {}", executionInfo);
        return executionInfo().from(executionInfo)
                .withExecutionStatus(INPROGRESS)
                .withShouldRetry(true)
                .build();
    }
    
    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        // Return 3 retry attempts with delays: 1s, 2s, 3s
        return Optional.of(List.of(1L, 2L, 3L));
    }
}

