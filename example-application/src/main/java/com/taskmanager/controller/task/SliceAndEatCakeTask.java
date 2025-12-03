package com.taskmanager.controller.task;


import com.taskmanager.controller.task.data.SliceCake;
import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
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
