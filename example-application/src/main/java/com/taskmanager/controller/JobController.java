package com.taskmanager.controller;

import com.taskmanager.controller.task.data.OvenSettings;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.domain.ExecutionStatus;
import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.persistence.service.JobService;
import com.taskmanager.service.ExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static java.time.ZonedDateTime.now;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    @Autowired
    public JobController(JobService jobService) {
        this.jobService = jobService;
    }
    @Autowired
    BakeryService bakeryService;
    
    @Autowired
    JsonObjectConverter jsonObjectConverter;
    
    @Autowired
    ExecutionService executionService;
    
    @PostMapping("/workflow")
    public void createWorkflowJob() {
        // Tasks are now auto-registered on application startup via TaskRegistry.autoRegisterTasks()
        bakeryService.makeCake();
    }
    
    @PostMapping("/oneoff")
    public ResponseEntity<Void> createOneOffJob() {
        // Create a job with OneOffTask
        final ExecutionInfo oneOffExecutionInfo = new ExecutionInfo(
                jsonObjectConverter.convertFromObject(new OvenSettings(200, 1, false)),
                "ONE_OFF_TASK",
                now(),
                ExecutionStatus.STARTED,
                false
        );
        
        executionService.executeWith(oneOffExecutionInfo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/oneoffwithretry")
    public ResponseEntity<Void> createOneOffJobWithRetry() {
        // Create a job with OneOffTask
        final ExecutionInfo oneOffExecutionInfo = new ExecutionInfo(
                jsonObjectConverter.convertFromObject(new OvenSettings(200, 1, false)),
                "ONE_OFF_TASK_WITH_RETRY",
                now(),
                ExecutionStatus.STARTED,
                false,1
        );
        
        executionService.executeWith(oneOffExecutionInfo);
        return ResponseEntity.ok().build();
    }

 
}
