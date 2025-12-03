package com.taskmanager.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.taskmanager.persistence.entity.Job;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Job} entities.
 * 
 * <p>This repository provides data access operations for jobs, including:
 * <ul>
 *   <li>Finding unassigned jobs (for job scheduling)</li>
 *   <li>Job CRUD operations</li>
 *   <li>Job assignment and release operations</li>
 *   <li>Job data and task details updates</li>
 * </ul>
 * 
 * <p>Query methods use pessimistic locking ({@link LockModeType#PESSIMISTIC_WRITE})
 * to prevent concurrent access issues when multiple workers are polling for jobs.
 * 
 * <p>Jobs are ordered by priority (ascending, 1 is highest) and then by start time
 * (ascending) to ensure high-priority jobs are processed first.
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see Job
 * @see com.taskmanager.persistence.service.JobService
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * SQL constant for inserting a new job (deprecated, use insertJob method).
     */
    String INSERT_JOB_SQL = "INSERT INTO jobs(job_id,worker_id,worker_lock_time,assigned_task_name,assigned_task_start_time,job_data,retry_attempts_remaining) values (?,?,?,?,?,to_jsonb(?::json),?)";
    
    /**
     * SQL constant for updating job data (deprecated, use updateJobData method).
     */
    String UPDATE_JOB_DATA_SQL = "UPDATE jobs SET job_data = to_jsonb(?::json) WHERE job_id = ?";
    
    /**
     * SQL constant for updating next task details (deprecated, use updateNextTaskDetails method).
     */
    String UPDATE_NEXT_TASK_DETAILS_SQL = "UPDATE jobs set assigned_task_name= ?, assigned_task_start_time= ?, retry_attempts_remaining= ? where job_id= ? ";
    
    /**
     * SQL constant for updating retry details (deprecated, use updateNextTaskRetryDetails method).
     */
    String UPDATE_NEXT_TASK_RETRY_DETAILS_SQL = "UPDATE jobs set assigned_task_start_time= ?, retry_attempts_remaining= ? where job_id= ? ";
    
    /**
     * SQL constant for deleting a job (deprecated, use deleteJob method).
     */
    String DELETE_JOB_SQL = "DELETE from jobs where job_id= ? ";
    
    /**
     * SQL constant for releasing a job lock (deprecated, use releaseJob method).
     */
    String RELEASE_JOB_SQL = "UPDATE jobs set worker_id= null, worker_lock_time= null where job_id= ? ";

    /**
     * Finds all unassigned jobs that are ready to be executed.
     * 
     * <p>This query uses pessimistic write locking to prevent concurrent access.
     * Jobs are ordered by priority (ascending) and then by start time (ascending).
     * 
     * <p>A job is considered unassigned if:
     * <ul>
     *   <li>{@code workerId} is null</li>
     *   <li>{@code assignedTaskStartTime} is less than or equal to the current time</li>
     * </ul>
     * 
     * @param currentTime the current time for comparison with job start times
     * @return a list of unassigned jobs ready for execution, ordered by priority and start time
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.workerId IS NULL AND j.assignedTaskStartTime <= :currentTime ORDER BY j.priority ASC, j.assignedTaskStartTime ASC")
    List<Job> findUnassignedJobs(@Param("currentTime") ZonedDateTime currentTime);

    /**
     * Finds unassigned jobs with pagination support.
     * 
     * <p>This method is similar to {@link #findUnassignedJobs(ZonedDateTime)} but supports
     * limiting the number of results using {@link Pageable}. This is useful for batch
     * processing to avoid loading too many jobs at once.
     * 
     * <p>Uses pessimistic write locking to prevent concurrent access.
     * 
     * @param currentTime the current time for comparison with job start times
     * @param pageable pagination information (page number and size)
     * @return a list of unassigned jobs ready for execution, limited by pageable,
     *         ordered by priority and start time
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.workerId IS NULL AND j.assignedTaskStartTime <= :currentTime ORDER BY j.priority ASC, j.assignedTaskStartTime ASC")
    List<Job> findUnassignedJobsWithLimit(@Param("currentTime") ZonedDateTime currentTime, Pageable pageable);

    /**
     * Finds a job by its unique identifier.
     * 
     * @param jobId the unique job identifier
     * @return an Optional containing the job if found, empty otherwise
     */
    Optional<Job> findByJobId(UUID jobId);

    /**
     * Inserts a new job into the database.
     * 
     * <p>This method uses a native SQL query to insert a job with all its fields.
     * The job data is converted to JSONB format for PostgreSQL storage.
     * 
     * @param jobId the unique job identifier
     * @param workerId the worker identifier (null for unassigned jobs)
     * @param workerLockTime the lock timestamp (null for unassigned jobs)
     * @param assignedTaskName the task name
     * @param assignedTaskStartTime the scheduled start time
     * @param jobData the job data as a JSON string
     * @param retryAttemptsRemaining the number of retry attempts remaining
     */
    @Modifying
    @Query(value = "INSERT INTO jobs(job_id,worker_id,worker_lock_time,assigned_task_name,assigned_task_start_time,job_data,retry_attempts_remaining) values (:jobId,:workerId,:workerLockTime,:assignedTaskName,:assignedTaskStartTime,to_jsonb(CAST(:jobData AS text)),:retryAttemptsRemaining)", nativeQuery = true)
    void insertJob(@Param("jobId") UUID jobId,
                   @Param("workerId") UUID workerId,
                   @Param("workerLockTime") ZonedDateTime workerLockTime,
                   @Param("assignedTaskName") String assignedTaskName,
                   @Param("assignedTaskStartTime") ZonedDateTime assignedTaskStartTime,
                   @Param("jobData") String jobData,
                   @Param("retryAttemptsRemaining") int retryAttemptsRemaining);

    /**
     * Updates the job data for a specific job.
     * 
     * <p>The job data is converted to JSONB format for PostgreSQL storage.
     * 
     * @param jobData the new job data as a JSON string
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "UPDATE jobs SET job_data = to_jsonb(CAST(:jobData AS text)) WHERE job_id = :jobId", nativeQuery = true)
    void updateJobData(@Param("jobData") String jobData,
                       @Param("jobId") UUID jobId);

    /**
     * Updates the next task details for a job.
     * 
     * <p>This method is used when a task completes and the workflow continues
     * with a different task. It updates the task name, start time, and retry attempts.
     * 
     * @param assignedTaskName the new task name
     * @param assignedTaskStartTime the new scheduled start time
     * @param retryAttemptsRemaining the new number of retry attempts remaining
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "UPDATE jobs set assigned_task_name= :assignedTaskName, assigned_task_start_time= :assignedTaskStartTime, retry_attempts_remaining= :retryAttemptsRemaining where job_id= :jobId", nativeQuery = true)
    void updateNextTaskDetails(@Param("assignedTaskName") String assignedTaskName,
                                @Param("assignedTaskStartTime") ZonedDateTime assignedTaskStartTime,
                                @Param("retryAttemptsRemaining") int retryAttemptsRemaining,
                                @Param("jobId") UUID jobId);

    /**
     * Updates the retry details for a job (start time and remaining attempts).
     * 
     * <p>This method is used when a task fails and needs to be retried.
     * It updates the start time to schedule the retry and decrements the retry attempts.
     * 
     * @param assignedTaskStartTime the new scheduled start time for the retry
     * @param retryAttemptsRemaining the updated number of retry attempts remaining
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "UPDATE jobs set assigned_task_start_time= :assignedTaskStartTime, retry_attempts_remaining= :retryAttemptsRemaining where job_id= :jobId", nativeQuery = true)
    void updateNextTaskRetryDetails(@Param("assignedTaskStartTime") ZonedDateTime assignedTaskStartTime,
                                     @Param("retryAttemptsRemaining") int retryAttemptsRemaining,
                                     @Param("jobId") UUID jobId);

    /**
     * Deletes a job from the database.
     * 
     * <p>This method is typically called when a job completes successfully
     * and is no longer needed.
     * 
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "DELETE from jobs where job_id= :jobId", nativeQuery = true)
    void deleteJob(@Param("jobId") UUID jobId);

    /**
     * Releases a job lock by clearing the worker assignment.
     * 
     * <p>This method sets {@code workerId} and {@code workerLockTime} to null,
     * making the job available for reassignment. It is used when:
     * <ul>
     *   <li>A task execution fails and the job needs to be retried</li>
     *   <li>A task execution completes and the workflow continues</li>
     *   <li>An error occurs during execution</li>
     * </ul>
     * 
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "UPDATE jobs set worker_id= null, worker_lock_time= null where job_id= :jobId", nativeQuery = true)
    void releaseJob(@Param("jobId") UUID jobId);
}

