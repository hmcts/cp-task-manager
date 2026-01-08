package uk.gov.hmcts.cp.taskmanager.example.controller.task;


import uk.gov.hmcts.cp.taskmanager.example.controller.task.data.Ingredients;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
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
