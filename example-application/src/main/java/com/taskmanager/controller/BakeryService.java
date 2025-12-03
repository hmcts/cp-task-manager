package com.taskmanager.controller;


import com.taskmanager.controller.task.data.OvenSettings;
import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.service.ExecutionService;
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
