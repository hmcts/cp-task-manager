package uk.gov.hmcts.cp.taskmanager.example.controller.task;



import uk.gov.hmcts.cp.taskmanager.example.controller.MakeCakeWorkflow;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;

import java.time.ZonedDateTime;

import static uk.gov.hmcts.cp.taskmanager.example.controller.MakeCakeWorkflow.CAKE_MADE;
import static uk.gov.hmcts.cp.taskmanager.example.controller.MakeCakeWorkflow.nextTask;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;

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
