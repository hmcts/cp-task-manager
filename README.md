# Task Manager Service

A Spring Boot-based distributed job scheduling and execution system that provides a robust framework for managing and executing tasks with priorities, retry logic, and scheduled execution times.

## Features

- **REST API** to create workflow and one-off jobs
- **PostgreSQL database** persistence with Liquibase schema management
- **Automatic job executor** that polls every 5 seconds (configurable) for unassigned jobs
- **Worker thread pool** for concurrent job execution (configurable pool size)
- **Priority-based scheduling** (1-10, where 1 is highest priority)
- **Retry mechanism** with configurable exponential backoff
- **Task auto-registration** using Spring dependency injection and `@Task` annotations
- **Transactional execution** ensuring ACID compliance
- **Job status tracking** via `JobStatus` DTO

## Project Structure

This is a multi-module Gradle project:

- **`task-manager-service`**: Core job scheduling and execution framework
- **`example-application`**: Example Spring Boot application demonstrating usage
- **`jobstore-liquibase`**: Database schema management via Liquibase changesets

## Prerequisites

- **Java 21** or higher
- **Gradle 9.1.0+** (wrapper included)
- **Docker and Docker Compose** (optional, for running PostgreSQL in a container)
- **PostgreSQL 15+** (if not using Docker)

## Database Setup

### Option 1: Using Docker (Recommended)

1. Start PostgreSQL using Docker Compose:
```bash
docker-compose up -d
```

This will:
- Start a PostgreSQL 15 container
- Create the database `job_scheduler_db`
- Set up user `postgres` with password `postgres`
- Expose PostgreSQL on port `5435` (mapped from container port 5432)

2. To stop the database:
```bash
docker-compose down
```

3. To stop and remove all data:
```bash
docker-compose down -v
```

### Option 2: Local PostgreSQL Installation

1. Create a PostgreSQL database:
```sql
CREATE DATABASE job_scheduler_db;
```

2. Update `example-application/src/main/resources/application.properties` with your PostgreSQL credentials if different from defaults:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5435/job_scheduler_db
spring.datasource.username=postgres
spring.datasource.password=postgres
```

## Running the Application

### Using Gradle

1. Build the project:
```bash
./gradlew build
```

2. Run the example application:
```bash
./gradlew :example-application:bootRun
```

### Using the Application Management Script

The `example-application/app.sh` script provides convenient commands for managing the application:

```bash
cd example-application
./app.sh start      # Start the application
./app.sh stop       # Stop the application
./app.sh restart    # Restart the application
./app.sh status     # Check application status
./app.sh logs       # View application logs
./app.sh curl       # Test API endpoints
```

The application will start on `http://localhost:8080`

## API Endpoints

### Create Workflow Job
Creates a multi-step workflow job (example: making a cake):
```bash
POST /api/jobs/workflow
```

### Create One-Off Job
Creates a simple one-off task:
```bash
POST /api/jobs/oneoff
```

### Create One-Off Job with Retry
Creates a one-off task with retry capability:
```bash
POST /api/jobs/oneoffwithretry
```

**Note**: The example application uses hardcoded task data. For production use, modify `JobController` to accept request bodies.

## Job Executor

The application includes a `JobExecutor` component that:

- Polls the database every 5 seconds (configurable) for unassigned jobs
- Finds jobs where `worker_id` IS NULL and `assigned_task_start_time` has passed
- Orders jobs by **priority ASC** (1 first), then by `assigned_task_start_time ASC`
- Limits batch size (default: 50 jobs per poll)
- Assigns jobs to worker threads (UUID-based worker IDs)
- Executes jobs concurrently using a configurable thread pool
- Manages worker locks and retry attempts

### Execution Flow

1. **Job Creation**: Jobs are created via `ExecutionService.executeWith(ExecutionInfo)`
2. **Job Discovery**: `JobExecutor` polls for unassigned jobs every 5 seconds
3. **Job Assignment**: Jobs are locked to worker threads using `worker_id` and `worker_lock_time`
4. **Task Execution**: `TaskExecutor` executes tasks in a transactional context
5. **Result Processing**: Jobs are updated, deleted, or scheduled for retry based on execution results

## Job Priority

Jobs can have a priority level from **1 to 10**:
- **1** - Highest priority, executed first
- **5** - Medium priority (default)
- **10** - Lowest priority, executed last

Within the same priority, jobs are ordered by `assigned_task_start_time`.

Priority can be set when creating a job via `ExecutionInfo`:
```java
ExecutionInfo info = new ExecutionInfo(
    jobData, 
    "TASK_NAME", 
    startTime, 
    ExecutionStatus.STARTED, 
    false, 
    1  // Priority: 1 (highest)
);
```

## Task System

### Creating a Task

Tasks are created by implementing the `ExecutableTask` interface and annotating with `@Task`:

```java
@Task("MY_TASK")
@Component
public class MyTask implements ExecutableTask {
    
    @Override
    public ExecutionInfo execute(ExecutionInfo executionInfo) {
        // Task logic here
        return executionInfo().from(executionInfo)
            .withExecutionStatus(COMPLETED)
            .build();
    }
    
    // Optional: Configure retry durations
    @Override
    public Optional<List<Long>> getRetryDurationsInSecs() {
        return Optional.of(List.of(10L, 20L, 30L));  // 3 retry attempts
    }
}
```

Tasks are **auto-registered** on application startup via `TaskRegistry.autoRegisterTasks()`.

### Task Execution Status

Tasks return an `ExecutionInfo` with one of these statuses:
- **STARTED**: Job created, waiting for execution
- **INPROGRESS**: Task executing or needs continuation (workflow)
- **COMPLETED**: Task finished successfully

## Configuration

All aspects of the job executor are configurable via `application.properties`:

```properties
# Job Executor Configuration
job.executor.poll-interval=5000                    # Poll interval in milliseconds
job.executor.core-pool-size=5                     # Core thread pool size
job.executor.max-pool-size=10                     # Maximum thread pool size
job.executor.queue-capacity=100                   # Queue capacity for jobs
job.executor.batch-size=50                        # Jobs fetched per poll
job.executor.thread-name-prefix=job-executor-     # Thread name prefix
job.executor.wait-for-tasks-on-shutdown=true     # Graceful shutdown
job.executor.await-termination-seconds=60         # Shutdown timeout

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5435/job_scheduler_db
spring.datasource.username=postgres
spring.datasource.password=postgres

# Liquibase Configuration
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:liquibase/jobstore-db-changelog.xml

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=validate
```

## Database Schema

The `jobs` table is automatically created via Liquibase with the following structure:

- `job_id` (UUID, Primary Key) - Auto-generated UUID
- `worker_id` (UUID) - UUID of the worker assigned to this job (NULL if unassigned)
- `worker_lock_time` (TIMESTAMP WITH TIME ZONE) - When the worker lock was acquired
- `assigned_task_name` (TEXT, Not Null) - Name of the task to execute
- `assigned_task_start_time` (TIMESTAMP WITH TIME ZONE, Not Null) - When the task should start
- `job_data` (JSONB, Not Null) - JSON data for the job (stored as PostgreSQL JSONB)
- `retry_attempts_remaining` (INTEGER, Not Null) - Number of retry attempts left
- `priority` (INTEGER, Not Null, Default: 10) - Job priority (1-10, where 1 is highest)

### Schema Management

Database schema is managed via Liquibase changesets in the `jobstore-liquibase` module:
- `001-initial-schema.xml`: Initial schema creation
- `002-rename-task-columns.xml`: Migration for column renaming (if upgrading)

## Retry Mechanism

Tasks can implement retry logic by:

1. Returning `INPROGRESS` status with `shouldRetry = true`
2. Implementing `getRetryDurationsInSecs()` to provide retry delay durations

**Example:**
```java
@Override
public ExecutionInfo execute(ExecutionInfo executionInfo) {
    // Simulate failure
    return executionInfo().from(executionInfo)
        .withExecutionStatus(INPROGRESS)
        .withShouldRetry(true)  // Signal retry needed
        .build();
}

@Override
public Optional<List<Long>> getRetryDurationsInSecs() {
    return Optional.of(List.of(10L, 20L, 30L));  // 3 retry attempts with delays
}
```

Retry sequence:
- Attempt 1 fails → Wait 10 seconds → Retry
- Attempt 2 fails → Wait 20 seconds → Retry
- Attempt 3 fails → Wait 30 seconds → Retry
- Attempt 4 fails → No more retries → Continue workflow or fail

## Architecture

The system consists of several key components:

- **`JobExecutor`**: Schedules and coordinates job execution (scheduler)
- **`TaskExecutor`**: Executes individual tasks in a transactional context (worker)
- **`TaskRegistry`**: Auto-discovers and manages task implementations
- **`ExecutionService`**: Creates and persists jobs
- **`JobService`**: Manages job persistence and database operations

For detailed architecture documentation, see [TASK_MANAGER_SERVICE_EXPLANATION.md](TASK_MANAGER_SERVICE_EXPLANATION.md).

## Development

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Running Specific Module Tests

```bash
./gradlew :task-manager-service:test
```

## License

This project is part of a task management system demonstration.
