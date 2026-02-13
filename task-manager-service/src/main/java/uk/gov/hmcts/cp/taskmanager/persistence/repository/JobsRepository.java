package uk.gov.hmcts.cp.taskmanager.persistence.repository;

import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
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
 * @see JobService
 */
@Repository
public interface JobsRepository extends JpaRepository<Job, UUID> {

    /**
     * Finds unassigned jobs with pagination support.
     *
     * <p>This method limit the number of results using {@link Pageable}. This is useful for batch
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
    @Query("SELECT j FROM Job j WHERE j.workerId IS NULL AND j.assignedTaskStartTime <= :currentTime ORDER BY j.priority ASC, j.assignedTaskStartTime ASC ")
    List<Job> findUnassignedJobsWithLimit(@Param("currentTime") ZonedDateTime currentTime, Pageable pageable);

    @Modifying
    @Query(value = """
                        WITH uaj AS (
                            SELECT job_id
                            FROM jobs
                            WHERE worker_id IS NULL
                              AND assigned_task_start_time <= :currentTime
                            ORDER BY priority ASC,
                                     assigned_task_start_time ASC
                            LIMIT :batchSize
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE jobs j
                        SET worker_id = :workerId,
                            worker_lock_time = :lockTime
                        FROM uaj
                        WHERE j.job_id = uaj.job_id
                        RETURNING j.*
                    """, nativeQuery = true)
    List<Job> assignJobsToWorkerBatch(@Param("workerId") UUID workerId,
                                @Param("lockTime") ZonedDateTime lockTime,
                                @Param("currentTime") ZonedDateTime currentTime,
                                @Param("batchSize") int batchSize);

    /**
     * Finds a job by its unique identifier.
     *
     * @param jobId the unique job identifier
     * @return an Optional containing the job if found, empty otherwise
     */
    Optional<Job> findByJobId(UUID jobId);

    /**
     * Updates the job data for a specific job.
     *
     * <p>The job data is converted to JSONB format for PostgreSQL storage.
     *
     * @param jobData the new job data as a JSON string
     * @param jobId the unique job identifier
     */
    @Modifying
    @Query(value = "UPDATE jobs SET job_data = :jobData WHERE job_id = :jobId", nativeQuery = true)
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

