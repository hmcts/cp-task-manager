package com.taskmanager.controller.task;



import com.taskmanager.controller.MakeCakeWorkflow;
import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;

import java.time.ZonedDateTime;

import static com.taskmanager.controller.MakeCakeWorkflow.CAKE_MADE;
import static com.taskmanager.controller.MakeCakeWorkflow.nextTask;
import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;
import static com.taskmanager.domain.ExecutionStatus.INPROGRESS;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobUtil {

    @Autowired
    JsonObjectConverter objectConverter;


    public ExecutionInfo nextJob(final ExecutionInfo prevExecutionInfo) {

        final MakeCakeWorkflow nextStep = nextTask(MakeCakeWorkflow.valueOf(prevExecutionInfo.getAssignedTaskName()));

        final ExecutionStatus nextExecutionStatus = MakeCakeWorkflow.valueOf(prevExecutionInfo.getAssignedTaskName()) == CAKE_MADE ? COMPLETED : INPROGRESS;

        return executionInfo().from(prevExecutionInfo)
                .withJobData(objectConverter.convertFromObject(nextStep.getTaskData()))
                .withAssignedTaskName(nextStep.toString())
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .withExecutionStatus(nextExecutionStatus)
                .build();
    }

    public ExecutionInfo sameJob(final Object jobData, final ZonedDateTime assignedTaskStartTime) {
        return executionInfo()
                .withShouldRetry(true)
                .withJobData(objectConverter.convertFromObject(jobData))
                .withAssignedTaskName(CAKE_MADE.toString())
                .withAssignedTaskStartTime(assignedTaskStartTime)
                .withExecutionStatus(INPROGRESS)
                .build();
    }
}
