package uk.gov.hmcts.cp.taskmanager.integration.tasks;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;

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

