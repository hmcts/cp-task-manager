package uk.gov.hmcts.cp.taskmanager.example.controller.task;


import uk.gov.hmcts.cp.taskmanager.example.controller.task.data.OvenSettings;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Task("SWITCH_OVEN_ON")
@Component
public class SwitchOvenOnTask implements ExecutableTask {

    private final Logger logger = LoggerFactory.getLogger(SwitchOvenOnTask.class);

    @Autowired
    private JsonObjectConverter jsonObjectConverter;

    @Autowired
    private JobUtil jobUtil;

    @Override
    public ExecutionInfo execute(ExecutionInfo job) {

        logger.info("Switching on oven [job {}]", job);

        final OvenSettings ovenSetting = jsonObjectConverter.convertToObject(job.getJobData(), OvenSettings.class);

        logger.info("Oven swtched on to temperarature {} degreesC, using steam function ? = {}, shelf no {} ready for cake tin", ovenSetting.getDegreesCelsius(),
                                                                                                                                 ovenSetting.isUseSteamFunction(),
                                                                                                                                 ovenSetting.getShelfNumber());
        
        // Return ExecutionInfo with shouldRetry=false to proceed to next task (jobUtil.nextJob sets shouldRetry=false by default)
        return jobUtil.nextJob(job);
    }

    // @Override
    // public Optional<List<Long>> getRetryDurationsInSecs() {
    //     return Optional.of(List.of(1L, 2L, 3L));
    // }
}
