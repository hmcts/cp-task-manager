package com.taskmanager.domain.executor;

import com.taskmanager.service.task.TaskRegistry;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.domain.ExecutionInfo;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static com.taskmanager.domain.ExecutionInfo.executionInfo;
import static com.taskmanager.domain.ExecutionStatus.COMPLETED;
import static com.taskmanager.domain.ExecutionStatus.INPROGRESS;
import static java.time.ZonedDateTime.now;

/**
 * Executes a single job's task within a transaction.
 * 
 * <p>TaskExecutor is responsible for executing a specific job's task. It implements
 * {@link Runnable} so it can be executed in a separate thread by the {@link JobExecutor}.
 * 
 * <p><b>Execution flow:</b>
 * <ol>
 *   <li>Retrieves the task from the {@link TaskRegistry} by task name</li>
 *   <li>Checks if the task start time has been reached</li>
 *   <li>Executes the task within a transaction</li>
 *   <li>Handles the execution result:
 *     <ul>
 *       <li>{@code COMPLETED} - Deletes the job</li>
 *       <li>{@code INPROGRESS} - Updates job with next task details or schedules retry</li>
 *     </ul>
 *   </li>
 *   <li>Releases the job lock on completion or error</li>
 * </ol>
 * 
 * <p><b>Retry logic:</b>
 * <p>If a task fails (returns {@code shouldRetry=true}), the executor checks:
 * <ul>
 *   <li>If retry attempts remain ({@code retryAttemptsRemaining > 0})</li>
 *   <li>If the task has retry durations configured</li>
 * </ul>
 * If both conditions are met, the job is scheduled for retry with the next retry duration.
 * 
 * <p><b>Transaction management:</b>
 * <p>All job updates are performed within a transaction. If an exception occurs,
 * the transaction is rolled back and the job lock is released so it can be retried.
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see JobExecutor
 * @see ExecutableTask
 * @see Job
 */
public class TaskExecutor implements Runnable{
    
    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);
    
    /**
     * The job to execute.
     */
    private  Job job;
    
    /**
     * Registry for task lookup.
     */
    private  TaskRegistry taskRegistry;
    
    /**
     * Service for job persistence operations.
     */
    private  JobService jobService;
    
    /**
     * Transaction template for transactional operations.
     */
    private  TransactionTemplate transactionTemplate;

    /**
     * Constructs a new TaskExecutor for the specified job.
     * 
     * @param jobData the job to execute, must not be null
     * @param taskRegistry the task registry for task lookup, must not be null
     * @param jobService the job service for persistence operations, must not be null
     * @param transactionManager the transaction manager, must not be null
     */
    public TaskExecutor(final Job jobData,
                       final TaskRegistry taskRegistry,
                       final JobService jobService,
                       final PlatformTransactionManager transactionManager) {
        this.job = jobData;
        this.taskRegistry = taskRegistry;
        this.jobService = jobService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executes the job's task within a transaction.
     * 
     * <p>This method:
     * <ol>
     *   <li>Retrieves the task from the registry</li>
     *   <li>Checks if the task start time has been reached</li>
     *   <li>Executes the task within a transaction</li>
     *   <li>Handles the result (update job, delete job, or schedule retry)</li>
     *   <li>Releases the job lock on completion or error</li>
     * </ol>
     * 
     * <p>If the task is not found or an exception occurs, the job lock is released
     * so it can be retried or handled appropriately.
     */
    @Override
    @SuppressWarnings("squid:S3457")
    public void run() {
        final String taskName = job.getAssignedTaskName();
        logger.info("Invoking {} task: ", taskName);
        final Optional<ExecutableTask> task = taskRegistry.getTask(taskName);

        try {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    if (task.isPresent()) {
                        final ExecutionInfo executionInfo = executionInfo().fromJob(job).build();

                        if (isStartTimeOfTask(executionInfo)) {
                            executeTask(task.get(), executionInfo);
                        } else {
                            logger.debug("Task start time not reached yet for job {}", job.getJobId());
                            jobService.releaseJob(job.getJobId());
                        }
                    } else {
                        logger.error("No task registered to process this job {}", job.getJobId());
                        jobService.releaseJob(job.getJobId());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected exception during transaction for Job {}, transaction will be rolled back automatically. Releasing job lock: {}", this, e.getMessage(), e);
            // Release the job lock if transaction fails, so it can be retried
            try {
                jobService.releaseJob(job.getJobId());
            } catch (Exception releaseEx) {
                logger.error("Failed to release job lock for job {}: {}", job.getJobId(), releaseEx.getMessage(), releaseEx);
            }
        }
    }

    /**
     * Returns a string representation of this TaskExecutor.
     * 
     * @return a string containing the job information
     */
    @Override
    public String toString() {
        return "JobExecutor[ " +
                "job=" + job +
                "]";
    }

    /**
     * Checks if the task start time has been reached.
     * 
     * <p>A task can only be executed if its scheduled start time is in the past
     * or equal to the current time.
     * 
     * @param executionInfo the execution information containing the start time
     * @return true if the start time has been reached, false otherwise
     */
    private boolean isStartTimeOfTask(final ExecutionInfo executionInfo) {
        final ZonedDateTime assignedTaskStartTime = executionInfo.getAssignedTaskStartTime();
        final ZonedDateTime now = now();

        return assignedTaskStartTime.isBefore(now) || assignedTaskStartTime.isEqual(now);
    }

    /**
     * Executes the task and handles the result.
     * 
     * <p>This method:
     * <ul>
     *   <li>Executes the task with the provided execution info</li>
     *   <li>If status is {@code COMPLETED}, deletes the job</li>
     *   <li>If status is {@code INPROGRESS}:
     *     <ul>
     *       <li>Checks if retry is needed and possible</li>
     *       <li>If retry needed, schedules retry with next duration</li>
     *       <li>Otherwise, updates job with next task details and releases lock</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * @param task the task to execute, must not be null
     * @param executionInfo the execution information, must not be null
     */
    private void executeTask(final ExecutableTask task, final ExecutionInfo executionInfo) {
        final ExecutionInfo executionResponse = task.execute(executionInfo);

        if (executionResponse.getExecutionStatus().equals(INPROGRESS)) {
            if (canRetry(task, executionResponse)) {
                performRetry(task);
            } else {
                // If retries are exhausted, preserve the current retryAttemptsRemaining (0) instead of resetting it
                // Only reset to initial value if we're moving to a different task
                final Integer retryAttemptsRemaining = executionResponse.getAssignedTaskName().equals(job.getAssignedTaskName()) 
                    ? job.getRetryAttemptsRemaining() 
                    : taskRegistry.findRetryAttemptsRemainingFor(executionResponse.getAssignedTaskName());
                jobService.updateJobTaskData(job.getJobId(), executionResponse.getJobData());
                jobService.updateNextTaskDetails(job.getJobId(), executionResponse.getAssignedTaskName(), executionResponse.getAssignedTaskStartTime(), retryAttemptsRemaining);
                jobService.releaseJob(job.getJobId());
            }
        } else if (executionResponse.getExecutionStatus().equals(COMPLETED)) {
            jobService.deleteJob(job.getJobId());
        }
    }

    /**
     * Determines if a task can be retried.
     * 
     * <p>A task can be retried if all of the following conditions are met:
     * <ul>
     *   <li>The task response indicates it should be retried ({@code shouldRetry=true})</li>
     *   <li>There are retry attempts remaining ({@code retryAttemptsRemaining > 0})</li>
     *   <li>The task has retry durations configured</li>
     * </ul>
     * 
     * @param task the task that was executed
     * @param taskResponse the execution response from the task
     * @return true if the task can be retried, false otherwise
     */
    private boolean canRetry(final ExecutableTask task, final ExecutionInfo taskResponse) {
        final boolean shouldRetryTask = taskResponse.isShouldRetry();
        final Integer retryAttemptsRemaining = job.getRetryAttemptsRemaining();
        final boolean taskHasRetryDurationsConfigured = task.getRetryDurationsInSecs().isPresent();

        logger.info("Checking if task is retryable, jobID:{}, executionInfo.shouldRetry:{}, retryAttemptsRemaining:{}, has task configured with retryDurationsInSecs:{}",
                job.getJobId(), shouldRetryTask, retryAttemptsRemaining, taskHasRetryDurationsConfigured);

        return shouldRetryTask
                && retryAttemptsRemaining > 0
                && taskHasRetryDurationsConfigured;
    }

    /**
     * Schedules a retry for the task.
     * 
     * <p>This method:
     * <ul>
     *   <li>Gets the retry durations from the task</li>
     *   <li>Calculates the next retry duration based on remaining attempts</li>
     *   <li>Updates the job with the new start time and decremented retry attempts</li>
     *   <li>Releases the job lock so it can be picked up again</li>
     * </ul>
     * 
     * <p>The retry duration is selected from the list based on the number of attempts remaining.
     * For example, if there are 3 retry durations [10, 20, 30] and 2 attempts remaining,
     * the second duration (20) is used.
     * 
     * @param currentTask the task that failed and needs retry
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void performRetry(final ExecutableTask currentTask) {
        final List<Long> retryDurations = currentTask.getRetryDurationsInSecs().get();
        final Integer retryAttemptsRemaining = job.getRetryAttemptsRemaining();
        final Long retryDurationInSecs = retryDurations.get(retryDurations.size() - retryAttemptsRemaining);
        final ZonedDateTime exhaustTaskStartTime = now().plusSeconds(retryDurationInSecs);

        logger.info("Updating task retryDetails to performRetry, jobID: {}, retryAttemptsRemaining: {}, taskToExecuteOnRetriesExhaust: {}, exhaustTaskStartTime: {}",
                job.getJobId(), job.getRetryAttemptsRemaining(), job.getAssignedTaskName(), exhaustTaskStartTime);

        jobService.updateNextTaskRetryDetails(job.getJobId(), exhaustTaskStartTime, retryAttemptsRemaining-1);
        jobService.releaseJob(job.getJobId());
    }
}
