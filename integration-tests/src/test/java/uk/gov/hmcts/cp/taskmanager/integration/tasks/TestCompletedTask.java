package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

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

