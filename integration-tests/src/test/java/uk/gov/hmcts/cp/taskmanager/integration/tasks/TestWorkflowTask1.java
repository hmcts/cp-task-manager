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
 * First step in a test workflow.
 * Returns INPROGRESS to continue to next task.
 */
@Task("TEST_WORKFLOW_TASK_1")
@Component
public class TestWorkflowTask1 implements ExecutableTask {
    
    private static final Logger logger = LoggerFactory.getLogger(TestWorkflowTask1.class);
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("TestWorkflowTask1 executing for job: {}", executionInfo);
        return executionInfo().from(executionInfo)
                .withAssignedTaskName("TEST_WORKFLOW_TASK_2")
                .withExecutionStatus(INPROGRESS)
                .build();
    }
}

