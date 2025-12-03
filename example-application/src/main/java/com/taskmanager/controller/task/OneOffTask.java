package com.taskmanager.controller.task;

import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;

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
