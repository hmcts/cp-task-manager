package com.taskmanager.domain.executor;

import com.taskmanager.service.task.TaskRegistry;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.service.JobService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;

/**
 * Main scheduler component that polls for unassigned jobs and assigns them to worker threads.
 * 
 * <p>The JobExecutor is the core component of the task management system. It periodically
 * polls the database for unassigned jobs that are ready to be executed and assigns them
 * to worker threads for processing.
 * 
 * <p><b>Key responsibilities:</b>
 * <ul>
 *   <li>Periodically polling for unassigned jobs (via {@link Scheduled} annotation)</li>
 *   <li>Assigning jobs to worker threads</li>
 *   <li>Managing a thread pool for concurrent job execution</li>
 *   <li>Handling job assignment failures and retry logic</li>
 * </ul>
 * 
 * <p><b>Configuration:</b>
 * <p>All thread pool and polling parameters are configurable via application properties:
 * <ul>
 *   <li>{@code job.executor.poll-interval} - Polling interval in milliseconds (default: 5000)</li>
 *   <li>{@code job.executor.core-pool-size} - Core thread pool size (default: 5)</li>
 *   <li>{@code job.executor.max-pool-size} - Maximum thread pool size (default: 10)</li>
 *   <li>{@code job.executor.queue-capacity} - Queue capacity for pending jobs (default: 100)</li>
 *   <li>{@code job.executor.thread-name-prefix} - Thread name prefix (default: "job-executor-")</li>
 *   <li>{@code job.executor.wait-for-tasks-on-shutdown} - Wait for tasks on shutdown (default: true)</li>
 *   <li>{@code job.executor.await-termination-seconds} - Await termination seconds (default: 60)</li>
 *   <li>{@code job.executor.batch-size} - Batch size for fetching jobs (default: 50)</li>
 * </ul>
 * 
 * <p><b>Execution flow:</b>
 * <ol>
 *   <li>{@link #checkAndAssignJobs()} is called periodically by Spring's scheduler</li>
 *   <li>Fetches unassigned jobs (limited by batch size, ordered by priority)</li>
 *   <li>For each job:
 *     <ul>
 *       <li>Assigns the job to a worker (generates worker ID)</li>
 *       <li>Submits job execution to thread pool</li>
 *       <li>On failure, decrements retry attempts</li>
 *     </ul>
 *   </li>
 *   <li>Job execution is handled by {@link TaskExecutor} in a separate thread</li>
 * </ol>
 * 
 * <p>The thread pool is initialized on startup ({@link PostConstruct}) and shutdown
 * gracefully on application shutdown ({@link PreDestroy}).
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see TaskExecutor
 * @see JobService
 * @see TaskRegistry
 */
@Component
public class JobExecutor {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);

    /**
     * Service for job persistence operations.
     */
    private final JobService jobService;
    
    /**
     * Registry for task lookup.
     */
    private final TaskRegistry taskRegistry;
    
    /**
     * Transaction manager for transactional operations.
     */
    private final PlatformTransactionManager transactionManager;
    
    /**
     * Thread pool executor for concurrent job execution.
     */
    private final ThreadPoolTaskExecutor executor;

    /**
     * Polling interval in milliseconds for checking unassigned jobs.
     * Configurable via {@code job.executor.poll-interval} property.
     */
    @Value("${job.executor.poll-interval:5000}")
    private long pollInterval;

    /**
     * Core thread pool size.
     * Configurable via {@code job.executor.core-pool-size} property.
     */
    @Value("${job.executor.core-pool-size:5}")
    private int corePoolSize;

    /**
     * Maximum thread pool size.
     * Configurable via {@code job.executor.max-pool-size} property.
     */
    @Value("${job.executor.max-pool-size:10}")
    private int maxPoolSize;

    /**
     * Queue capacity for pending job executions.
     * Configurable via {@code job.executor.queue-capacity} property.
     */
    @Value("${job.executor.queue-capacity:100}")
    private int queueCapacity;

    /**
     * Thread name prefix for worker threads.
     * Configurable via {@code job.executor.thread-name-prefix} property.
     */
    @Value("${job.executor.thread-name-prefix:job-executor-}")
    private String threadNamePrefix;

    /**
     * Whether to wait for tasks to complete on shutdown.
     * Configurable via {@code job.executor.wait-for-tasks-on-shutdown} property.
     */
    @Value("${job.executor.wait-for-tasks-on-shutdown:true}")
    private boolean waitForTasksOnShutdown;

    /**
     * Maximum seconds to wait for task completion on shutdown.
     * Configurable via {@code job.executor.await-termination-seconds} property.
     */
    @Value("${job.executor.await-termination-seconds:60}")
    private int awaitTerminationSeconds;

    /**
     * Batch size for fetching unassigned jobs per polling cycle.
     * Configurable via {@code job.executor.batch-size} property.
     */
    @Value("${job.executor.batch-size:50}")
    private int batchSize;

    /**
     * Constructs a new JobExecutor with the specified dependencies.
     * 
     * @param jobService the job service for persistence operations, must not be null
     * @param taskRegistry the task registry for task lookup, must not be null
     * @param transactionManager the transaction manager, must not be null
     */
    @Autowired
    public JobExecutor(JobService jobService, TaskRegistry taskRegistry, PlatformTransactionManager transactionManager) {
        this.jobService = jobService;
        this.taskRegistry = taskRegistry;
        this.transactionManager = transactionManager;
        this.executor = new ThreadPoolTaskExecutor();
    }

    /**
     * Initializes the thread pool executor with configured parameters.
     * 
     * <p>This method is called after dependency injection completes. It configures
     * the thread pool with all the settings from application properties and initializes it.
     */
    @PostConstruct
    public void init() {
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksOnShutdown);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
    }

    /**
     * Shuts down the thread pool executor gracefully.
     * 
     * <p>This method is called when the application is shutting down. It ensures
     * that all running tasks complete before the executor is terminated.
     */
    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }

    /**
     * Periodically checks for unassigned jobs and assigns them to worker threads.
     * 
     * <p>This method is scheduled to run at fixed intervals (configurable via
     * {@code job.executor.poll-interval}). It:
     * <ol>
     *   <li>Fetches unassigned jobs (limited by batch size, ordered by priority)</li>
     *   <li>For each job:
     *     <ul>
     *       <li>Generates a unique worker ID</li>
     *       <li>Assigns the job to the worker</li>
     *       <li>Submits job execution to the thread pool</li>
     *       <li>On failure, decrements retry attempts</li>
     *     </ul>
     *   </li>
     * </ol>
     * 
     * <p>Jobs are ordered by priority (ascending, 1 is highest) and then by start time
     * (ascending), ensuring high-priority jobs are processed first.
     * 
     * <p>If an error occurs during job assignment, the retry attempts are decremented
     * so the job can be retried in the next polling cycle.
     */
    @Scheduled(fixedDelayString = "${job.executor.poll-interval:5000}")
    public void checkAndAssignJobs() {
        try {
            logger.debug("Checking for unassigned jobs (batch size: {})...", batchSize);
            List<Job> unassignedJobs = jobService.getUnassignedJobs(batchSize);

            if (unassignedJobs.isEmpty()) {
                logger.debug("No unassigned jobs found");
                return;
            }

            logger.info("Found {} unassigned job(s)", unassignedJobs.size());

            for (Job job : unassignedJobs) {
                try {
                    // Assign job to a worker
                    UUID workerId = UUID.randomUUID();
                    Job assignedJob = jobService.assignJobToWorker(job.getJobId(), workerId);
                    logger.info("Assigned job {} to worker {}", assignedJob.getJobId(), workerId);

                    // Submit job execution to worker thread pool
                    executor.execute(() -> executeJob(assignedJob));
                } catch (Exception e) {
                    logger.error("Error assigning job {}: {}", job.getJobId(), e.getMessage(), e);
                    // Decrement retry attempts on failure
                    try {
                        jobService.decrementRetryAttempts(job.getJobId());
                    } catch (Exception ex) {
                        logger.error("Error decrementing retry attempts for job {}: {}", job.getJobId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in job executor: {}", e.getMessage(), e);
        }
    }

    /**
     * Executes a job by creating a TaskExecutor and submitting it to the thread pool.
     * 
     * <p>This method creates a new {@link TaskExecutor} instance for the job and submits
     * it to the thread pool for execution. The TaskExecutor will handle the actual task
     * execution, including transaction management and error handling.
     * 
     * @param job the job to execute, must not be null and must have a worker assigned
     */
    private void executeJob(Job job) {
        UUID workerId = job.getWorkerId();
        logger.info("Worker {} starting execution of job {} (task: {})", workerId, job.getJobId(), job.getAssignedTaskName());

        executor.execute(new TaskExecutor(
                job,
                taskRegistry,
                jobService,
                transactionManager));
    }
}
