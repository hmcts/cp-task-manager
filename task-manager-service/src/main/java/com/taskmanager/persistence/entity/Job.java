package com.taskmanager.persistence.entity;

import com.taskmanager.domain.converter.JsonObjectConverter;
import jakarta.json.JsonObject;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * JPA entity representing a job in the task management system.
 * 
 * <p>A Job represents a unit of work that needs to be executed. Each job contains:
 * <ul>
 *   <li>Task information (name, start time, data)</li>
 *   <li>Worker assignment (workerId, lock time) for distributed execution</li>
 *   <li>Retry configuration (remaining attempts)</li>
 *   <li>Priority (1-10, where 1 is highest)</li>
 * </ul>
 * 
 * <p>Jobs are persisted in the {@code jobs} table and are managed by the
 * {@link com.taskmanager.persistence.service.JobService}. The {@link com.taskmanager.domain.executor.JobExecutor}
 * polls for unassigned jobs and assigns them to workers for execution.
 * 
 * <p>Job lifecycle:
 * <ol>
 *   <li>Created with task details and scheduled start time</li>
 *   <li>Assigned to a worker when execution time is reached</li>
 *   <li>Executed by {@link com.taskmanager.domain.executor.TaskExecutor}</li>
 *   <li>Updated with next task details or deleted if completed</li>
 * </ol>
 * 
 * <p>The job data is stored as JSON using {@link JsonObjectConverter} for flexible
 * task-specific payloads.
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see com.taskmanager.persistence.service.JobService
 * @see com.taskmanager.domain.executor.JobExecutor
 * @see com.taskmanager.domain.executor.TaskExecutor
 */
@Entity
@Table(name = "jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    /**
     * Unique identifier for the job.
     * Automatically generated via {@link #onCreate()} if not provided.
     */
    @Id
    @Column(name = "job_id", columnDefinition = "UUID")
    private UUID jobId;

    /**
     * Identifier of the worker thread assigned to execute this job.
     * Null when the job is unassigned.
     */
    @Column(name = "worker_id", columnDefinition = "UUID")
    private UUID workerId;

    /**
     * Timestamp when the job was locked/assigned to a worker.
     * Used for optimistic locking and worker management.
     */
    @Column(name = "worker_lock_time")
    private ZonedDateTime workerLockTime;

    /**
     * The name of the task to be executed.
     * Must match a task registered in the {@link com.taskmanager.service.task.TaskRegistry}.
     */
    @NotBlank(message = "Assigned task name is required")
    @Column(name = "assigned_task_name", nullable = false)
    private String assignedTaskName;

    /**
     * The scheduled start time for task execution.
     * The task will not be executed before this time.
     */
    @NotNull(message = "Assigned task start time is required")
    @Column(name = "assigned_task_start_time", nullable = false)
    private ZonedDateTime assignedTaskStartTime;

    /**
     * Task-specific data payload stored as JSON.
     * Converted to/from database using {@link JsonObjectConverter}.
     */
    @Column(name = "job_data", columnDefinition = "TEXT")
    @Convert(converter = JsonObjectConverter.class)
    private JsonObject jobData;

    /**
     * Number of retry attempts remaining for this job.
     * Decremented on each retry attempt. Zero means no retries remaining.
     */
    @Column(name = "retry_attempts_remaining", nullable = false)
    private int retryAttemptsRemaining;

    /**
     * Job priority (1-10, where 1 is highest priority).
     * Jobs with higher priority (lower number) are picked first by the executor.
     * Defaults to 10 (lowest priority) if not specified.
     */
    @Min(value = 1, message = "Priority must be between 1 and 10")
    @Max(value = 10, message = "Priority must be between 1 and 10")
    @Column(name = "priority", nullable = false)
    private int priority = 10; // Default priority is 10 (lowest)

    /**
     * JPA lifecycle callback executed before persisting a new entity.
     * 
     * <p>This method ensures:
     * <ul>
     *   <li>A jobId is generated if not already set</li>
     *   <li>Priority defaults to 10 if set to 0</li>
     * </ul>
     */
    @PrePersist
    protected void onCreate() {
        if (jobId == null) {
            jobId = UUID.randomUUID();
        }
        if (priority == 0) {
            priority = 10; // Set default priority if not set
        }
    }

    /**
     * Constructs a new Job with default priority (10).
     * 
     * @param jobId the unique job identifier, may be null (will be generated)
     * @param jobData the task-specific JSON data, may be null
     * @param assignedTaskName the task name, must not be null
     * @param assignedTaskStartTime the scheduled start time, must not be null
     * @param workerId the worker identifier, null if unassigned
     * @param workerLockTime the lock timestamp, null if unassigned
     * @param retryAttemptsRemaining the number of retry attempts remaining
     */
    public Job(final UUID jobId,
               final JsonObject jobData,
               final String assignedTaskName,
               final ZonedDateTime assignedTaskStartTime,
               final UUID workerId,
               final ZonedDateTime workerLockTime,
               final Integer retryAttemptsRemaining) {
        this(jobId, jobData, assignedTaskName, assignedTaskStartTime, workerId, workerLockTime, retryAttemptsRemaining, 10);
    }

    /**
     * Constructs a new Job with all parameters including priority.
     * 
     * @param jobId the unique job identifier, may be null (will be generated)
     * @param jobData the task-specific JSON data, may be null
     * @param assignedTaskName the task name, must not be null
     * @param assignedTaskStartTime the scheduled start time, must not be null
     * @param workerId the worker identifier, null if unassigned
     * @param workerLockTime the lock timestamp, null if unassigned
     * @param retryAttemptsRemaining the number of retry attempts remaining
     * @param priority the job priority (1-10), null defaults to 10
     */
    public Job(final UUID jobId,
               final JsonObject jobData,
               final String assignedTaskName,
               final ZonedDateTime assignedTaskStartTime,
               final UUID workerId,
               final ZonedDateTime workerLockTime,
               final Integer retryAttemptsRemaining,
               final Integer priority) {
        this.jobId = jobId;
        this.workerId = workerId;
        this.workerLockTime = workerLockTime;
        this.jobData = jobData;
        this.assignedTaskName = assignedTaskName;
        this.assignedTaskStartTime = assignedTaskStartTime;
        this.retryAttemptsRemaining = retryAttemptsRemaining;
        this.priority = priority != null ? priority : 10;
    }

}

