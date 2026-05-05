package uk.gov.hmcts.cp.taskmanager.service;


import static java.util.UUID.randomUUID;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.executor.JobExecutor;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.task.TaskRegistry;

import java.util.UUID;

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
 *   <li>The job is persisted and will be picked up by the {@link JobExecutor}</li>
 * </ul>
 *
 * <p>This service is typically used by REST controllers or other application components
 * that need to schedule task execution.
 *
 * @author Task Manager Service
 * @see ExecutionInfo
 * @see Job
 * @see TaskRegistry
 * @see JobService
 * @since 1.0.0
 */
public class ExecutionService {

    /**
     * Service for job persistence operations.
     */
    private final JobService jobService;

    /**
     * Registry for task lookup and configuration.
     */
    private final TaskRegistry taskRegistry;

    public ExecutionService(final JobService jobService, final TaskRegistry taskRegistry) {
        this.jobService = jobService;
        this.taskRegistry = taskRegistry;
    }

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
     * <p>The created job will be picked up by the {@link JobExecutor}
     * when its scheduled start time is reached. Jobs are ordered by priority, so higher
     * priority jobs (lower number) will be executed first.
     *
     * @param executionInfo the execution information containing task details, data,
     *                      start time, and optional priority
     * @return unique job ID
     */
    public UUID executeWith(final ExecutionInfo executionInfo) {
        final Integer retryAttemptsRemaining = taskRegistry.findRetryAttemptsRemainingFor(executionInfo.getAssignedTaskName());
        final UUID jobId = randomUUID();
        final Integer priority = executionInfo.getPriority() != null ? executionInfo.getPriority() : 10;
        final Job job = new Job(jobId, executionInfo.getJobData(),
                executionInfo.getAssignedTaskName(), executionInfo.getAssignedTaskStartTime(), null, null, retryAttemptsRemaining, priority);
        jobService.insertJob(job);
        return jobId;
    }
}

