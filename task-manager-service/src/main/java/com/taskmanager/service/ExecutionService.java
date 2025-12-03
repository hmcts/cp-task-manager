package com.taskmanager.service;


import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.service.task.TaskRegistry;
import com.taskmanager.persistence.service.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static java.util.UUID.randomUUID;

/**
 * Service for creating and scheduling job executions.
 * 
 * <p>This service provides functionality to create new jobs based on execution information.
 * It coordinates between the {@link TaskRegistry} to determine retry configuration and
 * the {@link JobService} to persist the job.
 * 
 * <p>When a job is created:
 * <ul>
 *   <li>A unique job ID is generated</li>
 *   <li>Retry attempts are determined from the task's retry configuration</li>
 *   <li>Priority is set from ExecutionInfo (defaults to 10 if not specified)</li>
 *   <li>The job is persisted and will be picked up by the {@link com.taskmanager.domain.executor.JobExecutor}</li>
 * </ul>
 * 
 * <p>This service is typically used by REST controllers or other application components
 * that need to schedule task execution.
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see ExecutionInfo
 * @see Job
 * @see TaskRegistry
 * @see JobService
 */
@Component
public class ExecutionService  {

    /**
     * Service for job persistence operations.
     */
    @Autowired
    private JobService jobService;

    /**
     * Registry for task lookup and configuration.
     */
    @Autowired
    private TaskRegistry taskRegistry;

    /**
     * Creates and schedules a new job for execution based on the provided execution information.
     * 
     * <p>This method:
     * <ol>
     *   <li>Looks up the task in the registry to determine retry configuration</li>
     *   <li>Generates a unique job ID</li>
     *   <li>Determines the job priority (from ExecutionInfo or defaults to 10)</li>
     *   <li>Creates a new Job entity with the execution information</li>
     *   <li>Persists the job to the database</li>
     * </ol>
     * 
     * <p>The created job will be picked up by the {@link com.taskmanager.domain.executor.JobExecutor}
     * when its scheduled start time is reached. Jobs are ordered by priority, so higher
     * priority jobs (lower number) will be executed first.
     * 
     * @param executionInfo the execution information containing task details, data,
     *                     start time, and optional priority
     */
    public void executeWith(final ExecutionInfo executionInfo) {
        final Integer retryAttemptsRemaining = taskRegistry.findRetryAttemptsRemainingFor(executionInfo.getAssignedTaskName());
        final UUID jobId = randomUUID();
        final Integer priority = executionInfo.getPriority() != null ? executionInfo.getPriority() : 10;
        final Job job = new Job(jobId, executionInfo.getJobData(),
                executionInfo.getAssignedTaskName(), executionInfo.getAssignedTaskStartTime(), null, null, retryAttemptsRemaining, priority);
        jobService.insertJob(job);
    }

 
}

