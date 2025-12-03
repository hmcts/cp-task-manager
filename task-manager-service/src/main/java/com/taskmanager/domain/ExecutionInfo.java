package com.taskmanager.domain;


import com.taskmanager.persistence.entity.Job;
import jakarta.json.JsonObject;

import java.time.ZonedDateTime;

/**
 * Represents execution information for a task within the task management system.
 * 
 * <p>This immutable class contains all the necessary information for executing a task,
 * including job data, task details, execution status, retry configuration, and priority.
 * It is used both as input to task execution and as output from task execution to
 * indicate the result and next steps.
 * 
 * <p>ExecutionInfo objects are created using the {@link Builder} pattern, which provides
 * a fluent API for constructing instances. The builder can be obtained via
 * {@link #executionInfo()}.
 * 
 * <p><b>Usage examples:</b>
 * <pre>{@code
 * // Create from scratch
 * ExecutionInfo info = ExecutionInfo.executionInfo()
 *     .withJobData(jsonData)
     *     .withAssignedTaskName("TASK_NAME")
     *     .withAssignedTaskStartTime(ZonedDateTime.now())
 *     .withExecutionStatus(ExecutionStatus.STARTED)
 *     .withPriority(5)
 *     .build();
 * 
 * // Create from existing ExecutionInfo
 * ExecutionInfo updated = ExecutionInfo.executionInfo()
 *     .from(existingInfo)
 *     .withExecutionStatus(ExecutionStatus.COMPLETED)
 *     .build();
 * 
 * // Create from Job entity
 * ExecutionInfo fromJob = ExecutionInfo.executionInfo()
 *     .fromJob(job)
 *     .build();
 * }</pre>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see ExecutionStatus
 * @see Job
 */
public class ExecutionInfo {
    
    /**
     * The job data as a JSON object, containing task-specific payload.
     */
    private final JsonObject jobData;
    
    /**
     * The name of the task to be executed.
     */
    private final String assignedTaskName;
    
    /**
     * The scheduled start time for the task execution.
     */
    private final ZonedDateTime assignedTaskStartTime;
    
    /**
     * The current execution status of the task.
     */
    private final ExecutionStatus executionStatus;
    
    /**
     * Flag indicating whether the task should be retried if it fails.
     */
    private final boolean shouldRetry;
    
    /**
     * The priority of the job (1-10, where 1 is highest priority).
     * Null indicates default priority (10).
     */
    private final Integer priority;

    /**
     * Constructs a new ExecutionInfo with the specified parameters.
     * 
     * @param jobData the job data as JSON, may be null
     * @param assignedTaskName the task name, may be null
     * @param assignedTaskStartTime the scheduled start time, may be null
     * @param executionStatus the execution status, may be null
     */
    public ExecutionInfo(final JsonObject jobData,
                         final String assignedTaskName,
                         final ZonedDateTime assignedTaskStartTime,
                         final ExecutionStatus executionStatus) {
        this(jobData, assignedTaskName, assignedTaskStartTime, executionStatus, false, null);
    }

    /**
     * Constructs a new ExecutionInfo with the specified parameters including retry flag.
     * 
     * @param jobData the job data as JSON, may be null
     * @param assignedTaskName the task name, may be null
     * @param assignedTaskStartTime the scheduled start time, may be null
     * @param executionStatus the execution status, may be null
     * @param shouldRetry true if the task should be retried on failure
     */
    public ExecutionInfo(final JsonObject jobData,
                         final String assignedTaskName,
                         final ZonedDateTime assignedTaskStartTime,
                         final ExecutionStatus executionStatus,
                         boolean shouldRetry) {
        this(jobData, assignedTaskName, assignedTaskStartTime, executionStatus, shouldRetry, null);
    }

    /**
     * Constructs a new ExecutionInfo with all parameters including priority.
     * 
     * @param jobData the job data as JSON, may be null
     * @param assignedTaskName the task name, may be null
     * @param assignedTaskStartTime the scheduled start time, may be null
     * @param executionStatus the execution status, may be null
     * @param shouldRetry true if the task should be retried on failure
     * @param priority the job priority (1-10), null for default (10)
     */
    public ExecutionInfo(final JsonObject jobData,
                         final String assignedTaskName,
                         final ZonedDateTime assignedTaskStartTime,
                         final ExecutionStatus executionStatus,
                         boolean shouldRetry,
                         Integer priority) {
        this.jobData = jobData;
        this.assignedTaskName = assignedTaskName;
        this.assignedTaskStartTime = assignedTaskStartTime;
        this.executionStatus = executionStatus;
        this.shouldRetry = shouldRetry;
        this.priority = priority;
    }

    public String getAssignedTaskName() {
        return assignedTaskName;
    }

    public ZonedDateTime getAssignedTaskStartTime() {
        return assignedTaskStartTime;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public boolean isShouldRetry() {
        return shouldRetry;
    }

    /**
     * Creates a new Builder instance for constructing ExecutionInfo objects.
     * 
     * @return a new Builder instance
     */
    public static Builder executionInfo() {
        return new Builder();
    }

    public JsonObject getJobData() {
        return jobData;
    }

    public Integer getPriority() {
        return priority;
    }

    /**
     * Builder class for constructing ExecutionInfo instances using a fluent API.
     * 
     * <p>The builder provides methods to set each field individually and supports
     * copying from existing ExecutionInfo instances or Job entities.
     * 
     * <p><b>Example:</b>
     * <pre>{@code
     * ExecutionInfo info = ExecutionInfo.executionInfo()
     *     .withJobData(jsonData)
     *     .withGivenTask("TASK_NAME")
     *     .withGivenTaskStartTime(now())
     *     .withExecutionStatus(ExecutionStatus.STARTED)
     *     .withPriority(5)
     *     .build();
     * }</pre>
     */
    public static class Builder {

        private JsonObject jobData;
        private String assignedTaskName;
        private ZonedDateTime assignedTaskStartTime;
        private ExecutionStatus executionStatus;
        private boolean shouldRetry;
        private Integer priority;

        /**
         * Private constructor to prevent direct instantiation.
         * Use {@link ExecutionInfo#executionInfo()} to create a builder.
         */
        private Builder() {
        }

        /**
         * Copies all fields from an existing ExecutionInfo instance.
         * 
         * @param executionInfo the ExecutionInfo to copy from, must not be null
         * @return this builder instance for method chaining
         */
        public Builder from(final ExecutionInfo executionInfo) {
            this.jobData = executionInfo.jobData;
            this.assignedTaskName = executionInfo.assignedTaskName;
            this.assignedTaskStartTime = executionInfo.assignedTaskStartTime;
            this.executionStatus = executionInfo.executionStatus;
            this.shouldRetry = executionInfo.shouldRetry;
            this.priority = executionInfo.priority;
            return this;
        }

        /**
         * Builds and returns a new ExecutionInfo instance with the configured values.
         * 
         * <p>If {@code shouldRetry} is true, validates that jobData, assignedTaskName, and
         * assignedTaskStartTime are not null, as these are required for retry functionality.
         * 
         * @return a new ExecutionInfo instance
         * @throws RuntimeException if shouldRetry is true but required fields (jobData,
         *         assignedTaskName, assignedTaskStartTime) are null
         */
        public ExecutionInfo build() {
            final boolean exhaustTaskDetailsNotConfigured = jobData == null || assignedTaskName == null || assignedTaskStartTime == null;

            if(shouldRetry && exhaustTaskDetailsNotConfigured) {
                throw new RuntimeException("retry exhaust task details (jobData, assignedTaskName, assignedTaskStartTime) must not be null when shouldRetry is true");
            }

            return new ExecutionInfo(jobData, assignedTaskName, assignedTaskStartTime, executionStatus, shouldRetry, priority);
        }

        public Builder withJobData(final JsonObject jobData) {
            this.jobData = jobData;
            return this;
        }

        public Builder withAssignedTaskName(final String assignedTaskName) {
            this.assignedTaskName = assignedTaskName;
            return this;
        }

        public Builder withAssignedTaskStartTime(final ZonedDateTime assignedTaskStartTime) {
            this.assignedTaskStartTime = assignedTaskStartTime;
            return this;
        }

        public Builder withExecutionStatus(final ExecutionStatus executionStatus) {
            this.executionStatus = executionStatus;
            return this;
        }

        public Builder withShouldRetry(final boolean shouldRetry) {
            this.shouldRetry = shouldRetry;
            return this;
        }

        public Builder withPriority(final Integer priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Initializes the builder with values from a Job entity.
         * 
         * <p>This method sets:
         * <ul>
         *   <li>executionStatus to {@link ExecutionStatus#STARTED}</li>
         *   <li>jobData from the job</li>
         *   <li>assignedTaskName from the job</li>
         *   <li>assignedTaskStartTime from the job</li>
         *   <li>priority from the job</li>
         * </ul>
         * 
         * @param job the Job entity to copy from, must not be null
         * @return this builder instance for method chaining
         */
        public Builder fromJob(final Job job) {
            this.executionStatus = ExecutionStatus.STARTED;
            this.jobData = job.getJobData();
            this.assignedTaskName = job.getAssignedTaskName();
            this.assignedTaskStartTime = job.getAssignedTaskStartTime();
            this.priority = job.getPriority();
            return this;
        }
    }
}
