package com.taskmanager.service;

import com.taskmanager.domain.ScheduleJobEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class JobSchedulingListener {

    @Autowired
    private ExecutionService executionService;

    @EventListener
    public void on(ScheduleJobEvent event) {
        executionService.executeWith(event.executionInfo());
    }
}
