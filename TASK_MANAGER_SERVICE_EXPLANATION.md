# Task Manager Service - Comprehensive Architecture Explanation

## Table of Contents
1. [Overview](#overview)
2. [Core Components](#core-components)
3. [Job Executor](#job-executor)
4. [Task Executor](#task-executor)
5. [Task Registry and Task System](#task-registry-and-task-system)
6. [Complete Execution Flow](#complete-execution-flow)
7. [Key Concepts](#key-concepts)
8. [Configuration](#configuration)

---

## Overview

The `task-manager-service` module is a distributed job scheduling and execution system built on Spring Boot. It provides a robust framework for:
- **Job Scheduling**: Creating and managing jobs with priorities, retry logic, and scheduled execution times
- **Task Execution**: Executing business logic tasks in a controlled, transactional environment
- **Concurrent Processing**: Managing multiple jobs concurrently using thread pools
- **Task Discovery**: Auto-registering tasks using Spring's dependency injection and annotations

---

## Core Components

### 1. **Job Entity** (`Job.java`)
Represents a job in the system with the following key attributes:
- `jobId`: Unique identifier (UUID)
- `workerId`: ID of the worker thread assigned to execute the job
- `workerLockTime`: Timestamp when the job was locked to a worker
- `assignedTaskName`: Name of the task to execute (e.g., "ONE_OFF_TASK")
- `assignedTaskStartTime`: When the task should start executing
- `jobData`: JSON data payload for the task
- `retryAttemptsRemaining`: Number of retry attempts left
- `priority`: Priority level (1-10, where 1 is highest priority)

### 2. **ExecutionInfo** (`ExecutionInfo.java`)
A data transfer object that carries execution context:
- `jobData`: JSON payload
- `assignedTaskName`: Task name
- `assignedTaskStartTime`: Scheduled start time
- `executionStatus`: Current status (STARTED, INPROGRESS, COMPLETED)
- `shouldRetry`: Whether the task should be retried on failure
- `priority`: Optional priority (1-10)

### 3. **ExecutionService** (`ExecutionService.java`)
Service for creating new jobs:
- `executeWith(ExecutionInfo)`: Creates a new job from ExecutionInfo and persists it to the database

---

## Job Executor

### Purpose
The `JobExecutor` is the **scheduler/coordinator** component that:
- Polls the database for unassigned jobs
- Assigns jobs to worker threads
- Manages a thread pool for concurrent execution
- Handles job locking to prevent duplicate execution

### Architecture

```java
@Component
public class JobExecutor {
    private ThreadPoolTaskExecutor executor;  // Thread pool for workers
    private JobService jobService;            // Database operations
    private TaskRegistry taskRegistry;        // Task lookup
}
```

### Key Features

#### 1. **Scheduled Polling**
- Uses `@Scheduled` annotation to poll every 5 seconds (configurable)
- Queries database for unassigned jobs (`worker_id IS NULL`)
- Orders by **priority ASC** (1 first), then by `assignedTaskStartTime ASC`
- Limits batch size (default: 50 jobs per poll)

#### 2. **Thread Pool Management**
- **Core Pool Size**: 5 threads (always active)
- **Max Pool Size**: 10 threads (can scale up)
- **Queue Capacity**: 100 jobs (queued before rejection)
- All configurable via `application.properties`

#### 3. **Job Assignment Process**

```
1. Poll Database
   └─> Query: SELECT * FROM jobs 
       WHERE worker_id IS NULL 
       AND assigned_task_start_time <= NOW()
       ORDER BY priority ASC, assigned_task_start_time ASC
       LIMIT batchSize

2. For Each Unassigned Job:
   ├─> Generate unique workerId (UUID)
   ├─> Lock job: SET worker_id = workerId, worker_lock_time = NOW()
   └─> Submit to thread pool: executor.execute(() -> executeJob(job))

3. Execute Job (in separate thread)
   └─> Create TaskExecutor and submit to thread pool
```

#### 4. **Error Handling**
- If job assignment fails, decrements retry attempts
- Logs errors for monitoring
- Continues processing other jobs

### Configuration

All aspects of `JobExecutor` are configurable:

```properties
job.executor.poll-interval=5000                    # Poll every 5 seconds
job.executor.core-pool-size=5                     # Core threads
job.executor.max-pool-size=10                     # Max threads
job.executor.queue-capacity=100                   # Queue size
job.executor.batch-size=50                        # Jobs per poll
job.executor.thread-name-prefix=job-executor-     # Thread names
job.executor.wait-for-tasks-on-shutdown=true     # Graceful shutdown
job.executor.await-termination-seconds=60         # Shutdown timeout
```

---

## Task Executor

### Purpose
The `TaskExecutor` is the **worker** component that:
- Executes individual tasks in a transactional context
- Handles task execution logic
- Manages retry mechanisms
- Updates job state based on execution results

### Architecture

```java
public class TaskExecutor implements Runnable {
    private Job job;                              // Job to execute
    private TaskRegistry taskRegistry;            // Task lookup
    private JobService jobService;               // Database operations
    private TransactionTemplate transactionTemplate; // Transaction management
}
```

### Execution Flow

#### 1. **Task Lookup**
```java
Optional<ExecutableTask> task = taskRegistry.getTask(job.getAssignedTaskName());
```
- Retrieves the task implementation from `TaskRegistry`
- Returns `Optional.empty()` if task not found

#### 2. **Start Time Validation**
```java
if (isStartTimeOfTask(executionInfo)) {
    executeTask(task, executionInfo);
} else {
    releaseJob(jobId);  // Too early, release for later
}
```
- Checks if `assignedTaskStartTime <= now()`
- If too early, releases the job lock so it can be picked up later

#### 3. **Transactional Execution**
```java
transactionTemplate.execute(() -> {
    ExecutionInfo result = task.execute(executionInfo);
    // Process result...
});
```
- Entire task execution runs in a database transaction
- Automatic rollback on exceptions
- Ensures data consistency

#### 4. **Result Processing**

**Status: INPROGRESS**
- Task needs to continue (workflow scenario)
- Updates job with new task details
- Releases job lock for next execution cycle

**Status: COMPLETED**
- Task finished successfully
- Deletes job from database

**Status: INPROGRESS + shouldRetry = true**
- Task failed but should retry
- Checks if retries available
- Schedules retry with exponential backoff

### Retry Logic

#### Retry Conditions
A task is retried if:
1. `executionResponse.isShouldRetry() == true`
2. `retryAttemptsRemaining > 0`
3. Task has `getRetryDurationsInSecs()` configured

#### Retry Scheduling
```java
List<Long> retryDurations = [10, 20, 30];  // seconds
int attemptsRemaining = 2;                  // 2 retries left
Long delay = retryDurations.get(retryDurations.size() - attemptsRemaining);
// delay = 20 seconds (second retry)

ZonedDateTime nextRetryTime = now().plusSeconds(delay);
updateNextTaskRetryDetails(jobId, nextRetryTime, attemptsRemaining - 1);
```

**Example Retry Sequence:**
- Attempt 1 fails → Wait 10 seconds → Retry
- Attempt 2 fails → Wait 20 seconds → Retry
- Attempt 3 fails → Wait 30 seconds → Retry
- Attempt 4 fails → No more retries → Continue workflow or fail

### Error Handling

- **Transaction Rollback**: Automatic on exceptions
- **Job Lock Release**: Ensures job can be retried
- **Logging**: Comprehensive error logging
- **Graceful Degradation**: Continues processing other jobs

---

## Task Registry and Task System

### Task Interface

```java
public interface ExecutableTask {
    ExecutionInfo execute(ExecutionInfo executionInfo);
    
    default Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.empty();  // No retry by default
    }
}
```

### Task Annotation

```java
@Task("TASK_NAME")
@Component
public class MyTask implements ExecutableTask {
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        // Task logic here
        return executionInfo().from(executionInfo)
            .withExecutionStatus(COMPLETED)
            .build();
    }
}
```

### Task Registry

#### Purpose
The `TaskRegistry` is a **central registry** that:
- Auto-discovers tasks on application startup
- Maps task names to task implementations
- Provides task lookup for `TaskExecutor`
- Manages retry configuration

#### Auto-Registration Process

```java
@PostConstruct
public void autoRegisterTasks() {
    // Spring provides all ExecutableTask beans
    for (ExecutableTask taskProxy : taskBeanProxy) {
        Class<?> actualClass = AopUtils.getTargetClass(taskProxy);
        Task annotation = actualClass.getAnnotation(Task.class);
        
        if (annotation != null) {
            String taskName = annotation.value();
            taskProxyByNameMap.put(taskName, taskProxy);
        }
    }
}
```

**How It Works:**
1. Spring scans for all `@Component` beans implementing `ExecutableTask`
2. `TaskRegistry` inspects each bean for `@Task` annotation
3. Extracts task name from annotation value
4. Registers task in internal map: `Map<String, ExecutableTask>`

#### Task Lookup

```java
Optional<ExecutableTask> getTask(String taskName) {
    return Optional.ofNullable(taskProxyByNameMap.get(taskName));
}
```

#### Retry Configuration Lookup

```java
Integer findRetryAttemptsRemainingFor(String taskName) {
    return getTask(taskName)
        .map(task -> task.getRetryDurationsInSecs().map(List::size).orElse(0))
        .orElse(0);
}
```

**Example:**
- Task has `getRetryDurationsInSecs() = [10, 20, 30]`
- Returns `3` (number of retry attempts available)

### Task Implementation Example

```java
@Task("ONE_OFF_TASK")
@Component
public class OneOffTask implements ExecutableTask {
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("Executing one-off task");
        
        // Process the task
        JsonObject jobData = executionInfo.getJobData();
        // ... business logic ...
        
        // Return completion status
        return executionInfo().from(executionInfo)
            .withExecutionStatus(COMPLETED)
            .build();
    }
    
    // No retry configuration = no retries
}
```

### Task with Retry Example

```java
@Task("ONE_OFF_TASK_WITH_RETRY")
@Component
public class OneOffTaskWithRetry implements ExecutableTask {
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        logger.info("Executing task with retry capability");
        
        // Simulate failure
        return executionInfo().from(executionInfo)
            .withExecutionStatus(INPROGRESS)
            .withShouldRetry(true)  // Signal retry needed
            .build();
    }
    
    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(List.of(10L, 20L, 30L));  // 3 retry attempts
    }
}
```

---

## Complete Execution Flow

### End-to-End Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Startup                          │
│                                                                 │
│  1. Spring scans for @Component beans                          │
│  2. TaskRegistry.autoRegisterTasks() discovers all tasks       │
│  3. Tasks registered in Map<String, ExecutableTask>           │
│  4. JobExecutor initializes thread pool                       │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Job Creation (ExecutionService)                    │
│                                                                 │
│  ExecutionService.executeWith(ExecutionInfo)                   │
│    ├─> Get retry attempts from TaskRegistry                   │
│    ├─> Create Job entity with priority                         │
│    └─> Persist to database                                     │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│            Job Executor - Scheduled Polling                     │
│                                                                 │
│  @Scheduled(every 5 seconds)                                   │
│    ├─> Query: Unassigned jobs                                  │
│    │     ORDER BY priority ASC, start_time ASC                 │
│    │     LIMIT batchSize                                       │
│    │                                                            │
│    ├─> For each job:                                           │
│    │     ├─> Generate workerId                                 │
│    │     ├─> Lock job (SET worker_id, worker_lock_time)        │
│    │     └─> Submit to thread pool                             │
│    │                                                            │
│    └─> Continue polling...                                      │
└─────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              Task Executor - Worker Thread                      │
│                                                                 │
│  TaskExecutor.run()                                             │
│    ├─> Get task from TaskRegistry                              │
│    ├─> Check start time (isStartTimeOfTask)                    │
│    │                                                            │
│    └─> Transaction:                                            │
│          ├─> Execute task.execute(executionInfo)               │
│          │                                                      │
│          ├─> Process result:                                   │
│          │   ├─> COMPLETED → Delete job                       │
│          │   ├─> INPROGRESS → Update & release                 │
│          │   └─> INPROGRESS + retry → Schedule retry           │
│          │                                                      │
│          └─> Commit or Rollback                                │
└─────────────────────────────────────────────────────────────────┘
```

### Detailed Step-by-Step Flow

#### Step 1: Job Creation
```java
// Client code
ExecutionInfo info = new ExecutionInfo(
    jobData, "ONE_OFF_TASK", now(), STARTED, false, 5  // priority 5
);
executionService.executeWith(info);

// ExecutionService
Job job = new Job(jobId, jobData, "ONE_OFF_TASK", 
                  startTime, null, null, retryAttempts, 5);
jobService.insertJob(job);  // Persist to database
// Job persisted with assigned_task_name and assigned_task_start_time columns
```

#### Step 2: Job Discovery
```java
// JobExecutor.checkAndAssignJobs() - runs every 5 seconds
List<Job> jobs = jobService.getUnassignedJobs(50);  
// Returns jobs ordered by: priority ASC, start_time ASC
```

#### Step 3: Job Assignment
```java
UUID workerId = UUID.randomUUID();
Job assignedJob = jobService.assignJobToWorker(jobId, workerId);
// Sets worker_id and worker_lock_time in database
```

#### Step 4: Task Execution
```java
// TaskExecutor.run()
Optional<ExecutableTask> task = taskRegistry.getTask("ONE_OFF_TASK");
ExecutionInfo result = task.get().execute(executionInfo);
```

#### Step 5: Result Processing
```java
if (result.getExecutionStatus() == COMPLETED) {
    jobService.deleteJob(jobId);  // Job finished
} else if (result.getExecutionStatus() == INPROGRESS) {
    if (canRetry(task, result)) {
        scheduleRetry(task);  // Retry with delay
    } else {
        updateJobAndRelease(result);  // Continue workflow
    }
}
```

---

## Key Concepts

### 1. **Job Locking**
- **Purpose**: Prevent multiple workers from executing the same job
- **Mechanism**: `worker_id` and `worker_lock_time` columns
- **Process**: 
  - Job assigned → `worker_id` set, `worker_lock_time` = NOW()
  - Job completed/released → `worker_id` = NULL, `worker_lock_time` = NULL

### 2. **Priority-Based Scheduling**
- **Range**: 1 (highest) to 10 (lowest)
- **Ordering**: Priority first, then start time
- **Use Case**: Critical jobs execute before normal jobs

### 3. **Retry Mechanism**
- **Configuration**: Task provides `getRetryDurationsInSecs()`
- **Execution**: Task sets `shouldRetry = true` and `status = INPROGRESS`
- **Scheduling**: Exponential backoff based on retry durations
- **Tracking**: `retryAttemptsRemaining` decrements on each retry

### 4. **Transaction Management**
- **Scope**: Entire task execution runs in a transaction
- **Rollback**: Automatic on exceptions
- **Isolation**: Each task execution is isolated

### 5. **Concurrent Execution**
- **Thread Pool**: Configurable size (5-10 threads)
- **Queue**: Buffers jobs before execution
- **Isolation**: Each job executes independently

### 6. **Task Discovery**
- **Auto-Registration**: Spring DI + `@PostConstruct`
- **Annotation-Based**: `@Task("NAME")` identifies tasks
- **Type Safety**: Compile-time task name checking

---

## Configuration

### Application Properties

```properties
# Job Executor Configuration
job.executor.poll-interval=5000                    # Milliseconds
job.executor.core-pool-size=5                      # Core threads
job.executor.max-pool-size=10                      # Max threads
job.executor.queue-capacity=100                    # Queue size
job.executor.batch-size=50                         # Jobs per poll
job.executor.thread-name-prefix=job-executor-      # Thread naming
job.executor.wait-for-tasks-on-shutdown=true       # Graceful shutdown
job.executor.await-termination-seconds=60          # Shutdown timeout
```

### Database Configuration

The system uses PostgreSQL with:
- **Table**: `jobs`
- **Columns**: 
  - `assigned_task_name`: Name of the task to execute (TEXT)
  - `assigned_task_start_time`: Scheduled start time (TIMESTAMP WITH TIME ZONE)
- **Indexes**: On `worker_id`, `assigned_task_start_time`, `priority`
- **Liquibase**: Schema management via changesets

---

## Summary

The `task-manager-service` module provides a robust, scalable job scheduling and execution framework:

1. **JobExecutor**: Schedules and coordinates job execution
2. **TaskExecutor**: Executes tasks in transactional context
3. **TaskRegistry**: Auto-discovers and manages task implementations
4. **ExecutableTask**: Interface for business logic tasks
5. **Priority System**: Ensures high-priority jobs execute first
6. **Retry Logic**: Handles failures with configurable backoff
7. **Concurrent Processing**: Thread pool for parallel execution
8. **Transaction Safety**: ACID compliance for job execution

The architecture is designed for:
- **Scalability**: Thread pool and batch processing
- **Reliability**: Transactions, retries, and error handling
- **Flexibility**: Configurable via properties
- **Maintainability**: Clear separation of concerns

