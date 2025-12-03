package com.taskmanager.domain;

/**
 * Represents the execution status of a task or job within the task management system.
 * 
 * <p>This enum defines the possible states that a task can be in during its lifecycle:
 * <ul>
 *   <li>{@code STARTED} - The task has been initiated and is ready to begin execution</li>
 *   <li>{@code INPROGRESS} - The task is currently being executed and may have additional steps</li>
 *   <li>{@code COMPLETED} - The task has finished execution successfully</li>
 * </ul>
 * 
 * <p>The status transitions typically follow: STARTED → INPROGRESS → COMPLETED
 * 
 * @author Task Manager Service
 * @since 1.0.0
 */
public enum ExecutionStatus {

    /**
     * The task has been started and is ready to begin execution.
     * This is typically the initial state when a job is created.
     */
    STARTED, 
    
    /**
     * The task is currently in progress and may have additional steps to execute.
     * Tasks in this state can transition to COMPLETED or remain INPROGRESS if
     * there are subsequent tasks in a workflow.
     */
    INPROGRESS, 
    
    /**
     * The task has completed execution successfully.
     * Jobs with COMPLETED status are typically removed from the system.
     */
    COMPLETED;
}
