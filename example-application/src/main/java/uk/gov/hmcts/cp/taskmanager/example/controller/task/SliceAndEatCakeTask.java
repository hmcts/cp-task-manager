package uk.gov.hmcts.cp.taskmanager.example.controller.task;


import uk.gov.hmcts.cp.taskmanager.example.controller.task.data.SliceCake;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Task("CAKE_MADE")
@Component
public class SliceAndEatCakeTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(SliceAndEatCakeTask.class);

    @Autowired
    private JsonObjectConverter jsonObjectConverter;

    @Autowired
    private JobUtil jobUtil;

    @Override
    public ExecutionInfo execute(ExecutionInfo job) {

        logger.info("sliceCake [job {}]", job);
        final SliceCake sliceCake = jsonObjectConverter.convertToObject(job.getJobData(), SliceCake.class);
        return jobUtil.nextJob(job);
    }
}
