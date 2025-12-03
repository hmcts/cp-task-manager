package com.taskmanager.controller.task;


import com.taskmanager.controller.task.data.Ingredients;
import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Task("GET_INGREDIENTS")
@Component
public class GetIngredientsTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(GetIngredientsTask.class);

    @Autowired
    private JsonObjectConverter jsonObjectConverter;

    @Autowired
    private JobUtil jobUtil;

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        logger.info("ingredients [job {}]", executionInfo);

        final Ingredients ingredients = jsonObjectConverter.convertToObject(executionInfo.getJobData(), Ingredients.class);


        return jobUtil.nextJob(executionInfo);
    }
}
