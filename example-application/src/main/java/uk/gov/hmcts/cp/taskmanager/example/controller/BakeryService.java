package uk.gov.hmcts.cp.taskmanager.example.controller;


import uk.gov.hmcts.cp.taskmanager.example.controller.task.data.OvenSettings;
import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static java.time.ZonedDateTime.now;

@Component
public class BakeryService {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    JsonObjectConverter objectConverter;

    @Autowired
    ExecutionService executionService;

    @Transactional
    public void makeCake() {
        final MakeCakeWorkflow firstTask = MakeCakeWorkflow.firstTask();

        //final ExecutionInfo startCakeExecutionInfo = new ExecutionInfo(objectConverter.convert(firstTask.getTaskData()), firstTask.toString(), now(), ExecutionStatus.STARTED, true);
        final ExecutionInfo startCakeExecutionInfo = new ExecutionInfo(objectConverter.convertFromObject(new OvenSettings(210, 2, true)), "SWITCH_OVEN_ON", now(), ExecutionStatus.STARTED, true);

        executionService.executeWith(startCakeExecutionInfo);
    }
}
