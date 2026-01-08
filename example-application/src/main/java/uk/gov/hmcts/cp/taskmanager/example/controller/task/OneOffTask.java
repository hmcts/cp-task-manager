package uk.gov.hmcts.cp.taskmanager.example.controller.task;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

@Task("ONE_OFF_TASK")
@Component
public class OneOffTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(OneOffTask.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo prevExecutionInfo) {
        logger.info("ONE_OFF_TASK [job {}]", prevExecutionInfo);

        return executionInfo().from(prevExecutionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
    
}
