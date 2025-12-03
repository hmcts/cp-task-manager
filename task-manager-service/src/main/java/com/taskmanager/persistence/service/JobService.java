package com.taskmanager.persistence.service;

import com.taskmanager.domain.converter.JsonObjectConverter;
import com.taskmanager.persistence.entity.Job;
import com.taskmanager.persistence.repository.JobRepository;
import jakarta.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service class for managing job persistence and operations.
 * 
 * <p>This service provides a high-level interface for job-related database operations,
 * encapsulating the complexity of job management including:
 * <ul>
 *   <li>Finding and retrieving unassigned jobs</li>
 *   <li>Assigning jobs to workers</li>
 *   <li>Updating job data and task details</li>
 *   <li>Managing job lifecycle (create, update, delete, release)</li>
 *   <li>Handling retry attempts</li>
 * </ul>
 * 
 * <p>All methods are transactional to ensure data consistency. The service uses
 * pessimistic locking when querying for unassigned jobs to prevent concurrent
 * assignment issues.
 * 
 * <p>This service is used by:
 * <ul>
 *   <li>{@link com.taskmanager.domain.executor.JobExecutor} - for job scheduling and assignment</li>
 *   <li>{@link com.taskmanager.domain.executor.TaskExecutor} - for job updates during execution</li>
 *   <li>{@link com.taskmanager.service.ExecutionService} - for creating new jobs</li>
 * </ul>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see Job
 * @see JobRepository
 */
@Service
public class JobService {
    
    /**
     * Repository for job data access operations.
     */
    private final JobRepository jobRepository;
    
    /**
     * Converter for JSON object serialization/deserialization.
     */
    private final JsonObjectConverter jsonObjectConverter;

    /**
     * Constructs a new JobService with the specified dependencies.
     * 
     * @param jobRepository the job repository, must not be null
     * @param jsonObjectConverter the JSON object converter, must not be null
     */
    @Autowired
    public JobService(JobRepository jobRepository, JsonObjectConverter jsonObjectConverter) {
        this.jobRepository = jobRepository;
        this.jsonObjectConverter = jsonObjectConverter;
    }



    /**
     * Retrieves all unassigned jobs that are ready to be executed.
     * 
     * <p>This method finds jobs that:
     * <ul>
     *   <li>Have no worker assigned ({@code workerId} is null)</li>
     *   <li>Have reached their scheduled start time</li>
     * </ul>
     * 
     * <p>Jobs are ordered by priority (ascending, 1 is highest) and then by start time (ascending).
     * Uses pessimistic write locking to prevent concurrent access.
     * 
     * @return a list of unassigned jobs ready for execution, ordered by priority and start time
     */
    @Transactional
    public List<Job> getUnassignedJobs() {
        return jobRepository.findUnassignedJobs(ZonedDateTime.now());
    }

    /**
     * Retrieves unassigned jobs with a limit on the number of results.
     * 
     * <p>This method is similar to {@link #getUnassignedJobs()} but limits the number
     * of results returned. This is useful for batch processing to avoid loading too
     * many jobs at once and improve performance.
     * 
     * <p>Jobs are ordered by priority (ascending) and then by start time (ascending).
     * Uses pessimistic write locking to prevent concurrent access.
     * 
     * @param batchSize the maximum number of jobs to retrieve
     * @return a list of unassigned jobs ready for execution, limited to batchSize,
     *         ordered by priority and start time
     */
    @Transactional
    public List<Job> getUnassignedJobs(int batchSize) {
        Pageable pageable = PageRequest.of(0, batchSize);
        return jobRepository.findUnassignedJobsWithLimit(ZonedDateTime.now(), pageable);
    }

    /**
     * Assigns a job to a worker thread.
     * 
     * <p>This method locks a job by setting the workerId and workerLockTime.
     * Once assigned, the job will not be picked up by other workers.
     * 
     * @param jobId the unique job identifier
     * @param workerId the unique worker identifier
     * @return the updated job with worker assignment
     * @throws RuntimeException if the job is not found
     */
    @Transactional
    public Job assignJobToWorker(UUID jobId, UUID workerId) {
        Job job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));
        job.setWorkerId(workerId);
        job.setWorkerLockTime(ZonedDateTime.now());
        return jobRepository.save(job);
    }

    /**
     * Decrements the retry attempts remaining for a job.
     * 
     * <p>This method is called when a job assignment fails and needs to be retried.
     * It only decrements if there are retry attempts remaining (> 0).
     * 
     * @param jobId the unique job identifier
     * @throws RuntimeException if the job is not found
     */
    @Transactional
    public void decrementRetryAttempts(UUID jobId) {
        Job job = jobRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));
        
        if (job.getRetryAttemptsRemaining() > 0) {
            job.setRetryAttemptsRemaining(job.getRetryAttemptsRemaining() - 1);
            jobRepository.save(job);
        }
    }
    
    /**
     * Inserts a new job into the database.
     * 
     * <p>This method persists a new job entity. The jobId will be automatically
     * generated if not set (via {@link Job#onCreate()}).
     * 
     * @param job the job to insert, must not be null
     */
    @Transactional
    public void insertJob(Job job) {
        jobRepository.save(job);
    }

    /**
     * Updates the job data for a specific job.
     * 
     * <p>This method updates the JSON data payload of a job. The data is converted
     * to a JSON string before being stored in the database.
     * 
     * @param jobId the unique job identifier
     * @param data the new job data as a JsonObject
     */
    @Transactional
    public void updateJobTaskData(final UUID jobId, final JsonObject data) {
        String jobDataString = jsonObjectConverter.convertToDatabaseColumn(data);
        jobRepository.updateJobData(jobDataString, jobId);
    }

    /**
     * Updates the next task details for a job.
     * 
     * <p>This method is used when a task completes and the workflow continues with
     * a different task. It updates the task name, scheduled start time, and retry
     * attempts remaining.
     * 
     * @param jobId the unique job identifier
     * @param assignedTaskName the new task name
     * @param startTime the new scheduled start time
     * @param retryAttemptsRemaining the new number of retry attempts remaining
     */
    @Transactional
    public void updateNextTaskDetails(final UUID jobId, final String assignedTaskName, final ZonedDateTime startTime, final Integer retryAttemptsRemaining) {
        jobRepository.updateNextTaskDetails(assignedTaskName, startTime, retryAttemptsRemaining, jobId);
    }

    /**
     * Deletes a job from the database.
     * 
     * <p>This method is typically called when a job completes successfully and
     * is no longer needed.
     * 
     * @param jobId the unique job identifier
     */
    @Transactional
    public void deleteJob(final UUID jobId) {
        jobRepository.deleteJob(jobId);
    }

    /**
     * Releases a job lock by clearing the worker assignment.
     * 
     * <p>This method makes a job available for reassignment by clearing the
     * workerId and workerLockTime. It is used when:
     * <ul>
     *   <li>A task execution fails and the job needs to be retried</li>
     *   <li>A task execution completes and the workflow continues</li>
     *   <li>An error occurs during execution</li>
     * </ul>
     * 
     * @param jobId the unique job identifier
     */
    @Transactional
    public void releaseJob(final UUID jobId) {
        jobRepository.releaseJob(jobId);
    }

    /**
     * Updates the retry details for a job (start time and remaining attempts).
     * 
     * <p>This method is used when a task fails and needs to be retried. It updates
     * the scheduled start time for the retry and the number of retry attempts remaining.
     * 
     * <p>Note: This method is not marked as {@code @Transactional} because it is
     * typically called within an existing transaction context.
     * 
     * @param jobId the unique job identifier
     * @param startTime the new scheduled start time for the retry
     * @param retryAttemptsRemaining the updated number of retry attempts remaining
     */
    public void updateNextTaskRetryDetails(final UUID jobId, final ZonedDateTime startTime, final Integer retryAttemptsRemaining) {
        jobRepository.updateNextTaskRetryDetails(startTime, retryAttemptsRemaining, jobId);
    }
}

