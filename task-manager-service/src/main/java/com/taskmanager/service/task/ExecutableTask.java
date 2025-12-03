package com.taskmanager.service.task;

import com.taskmanager.domain.ExecutionInfo;

import java.util.List;
import java.util.Optional;

/**
 * Interface for executable tasks within the task management system.
 * 
 * <p>Implementations of this interface represent individual units of work that can be
 * executed as part of a job. Tasks must be annotated with {@link Task} to be
 * automatically discovered and registered by the {@link TaskRegistry}.
 * 
 * <p>Each task receives an {@link ExecutionInfo} object containing:
 * <ul>
 *   <li>Job data (JSON payload)</li>
 *   <li>Task name and start time</li>
 *   <li>Current execution status</li>
 *   <li>Retry configuration</li>
 * </ul>
 * 
 * <p>The task processes the execution info and returns an updated {@link ExecutionInfo}
 * that may indicate:
 * <ul>
 *   <li>{@code COMPLETED} - Task finished successfully, job will be deleted</li>
 *   <li>{@code INPROGRESS} - Task completed but workflow continues with next task</li>
 *   <li>{@code shouldRetry=true} - Task failed and should be retried (if retry configured)</li>
 * </ul>
 * 
 * <p><b>Example implementation:</b>
 * <pre>{@code
 * @Task("SWITCH_OVEN_ON")
 * @Component
 * public class SwitchOvenOnTask implements ExecutableTask {
 *     public ExecutionInfo execute(ExecutionInfo executionInfo) {
 *         // Process the task
 *         return ExecutionInfo.executionInfo()
 *             .from(executionInfo)
 *             .withExecutionStatus(ExecutionStatus.COMPLETED)
 *             .build();
 *     }
 * }
 * }</pre>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see Task
 * @see TaskRegistry
 * @see ExecutionInfo
 */
public interface ExecutableTask {

    /**
     * Executes the task with the provided execution information.
     * 
     * <p>This method performs the actual work of the task. It receives an {@link ExecutionInfo}
     * object containing all necessary data and context, processes it, and returns an updated
     * {@link ExecutionInfo} indicating the result of the execution.
     * 
     * <p>The returned {@link ExecutionInfo} determines the next action:
     * <ul>
     *   <li>If status is {@code COMPLETED}, the job will be deleted</li>
     *   <li>If status is {@code INPROGRESS}, the job will be updated with the next task details</li>
     *   <li>If {@code shouldRetry} is true and retry attempts remain, the task will be retried</li>
     * </ul>
     * 
     * @param executionInfo the execution information containing job data, task details, and status
     * @return updated execution information with the result of task execution, including
     *         next task details if the workflow continues, or completion status if finished
     * @throws RuntimeException if task execution fails (will trigger retry if configured)
     */
    ExecutionInfo execute(final ExecutionInfo executionInfo);

    /**
     * Returns the list of retry durations in seconds for this task.
     * 
     * <p>This method is optional and only needs to be overridden if the task requires
     * retry capability. When a task fails (returns {@code shouldRetry=true}), the system
     * will use these durations to schedule retry attempts.
     * 
     * <p>The retry durations are applied in order. For example, if the list is [10, 20, 30]:
     * <ul>
     *   <li>First retry: 10 seconds after failure</li>
     *   <li>Second retry: 20 seconds after first retry failure</li>
     *   <li>Third retry: 30 seconds after second retry failure</li>
     * </ul>
     * 
     * <p>The number of retry attempts is determined by the size of this list. The
     * {@code retryAttemptsRemaining} field in the job tracks how many retries are left.
     * 
     * @return an {@link Optional} containing a list of retry durations in seconds,
     *         or {@link Optional#empty()} if retries are not supported
     */
    default Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.empty();
    }
}
