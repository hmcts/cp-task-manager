package uk.gov.hmcts.cp.taskmanager.example.controller.task;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;

@Task("ONE_OFF_TASK_WITH_RETRY")
@Component
public class OneOffTaskWithRetry implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(OneOffTaskWithRetry.class);

    @Override
    public ExecutionInfo execute(ExecutionInfo prevExecutionInfo) {
        logger.info("ONE_OFF_TASK_WITH_RETRY [job {}]", prevExecutionInfo);

        // Return INPROGRESS with shouldRetry=true to trigger retry logic
        // The retry mechanism will use the durations from getRetryDurationsInSecs()
        return executionInfo().from(prevExecutionInfo)
                .withExecutionStatus(INPROGRESS)
                .withShouldRetry(true)
                .build();
    }

     @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(List.of(10L, 20L, 30L));
    }
    
}
