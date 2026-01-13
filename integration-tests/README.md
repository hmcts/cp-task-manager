# Integration Tests Module

This module contains comprehensive integration tests for the Task Manager Service, testing the complete functionality as described in `TASK_MANAGER_SERVICE_EXPLANATION.md`.

## Overview

The integration tests verify end-to-end behavior of the task management system, including:
- Job creation and execution
- Priority-based scheduling
- Retry mechanism
- Task registry auto-registration
- Workflow execution
- Concurrent job processing

## Test Structure

### Test Configuration

- **`IntegrationTestApplication`**: Spring Boot test application that enables scheduling and component scanning
- **`IntegrationTestConfiguration`**: Meta-annotation for integration tests with test database configuration
- **`application-test.properties`**: Test configuration using H2 in-memory database

### Test Tasks

The module includes test task implementations used for integration testing:

- **`TestCompletedTask`**: Task that completes immediately
- **`TestInProgressTask`**: Task that returns INPROGRESS status
- **`TestRetryTask`**: Task with retry configuration (3 retries with delays: 1s, 2s, 3s)
- **`TestWorkflowTask1`**: First step in a workflow
- **`TestWorkflowTask2`**: Second step in a workflow

### Test Classes

1. **`JobCreationAndExecutionIntegrationTest`**
   - Tests job creation via `ExecutionService`
   - Tests job execution and completion
   - Tests jobs with future start times
   - Tests job data persistence

2. **`PriorityBasedSchedulingIntegrationTest`**
   - Tests jobs ordered by priority (1-10)
   - Tests jobs with same priority ordered by start time
   - Tests high priority jobs executed first
   - Tests default priority (10)

3. **`RetryMechanismIntegrationTest`**
   - Tests retry task decrements attempts
   - Tests retry scheduled with delay
   - Tests retry exhaustion
   - Tests tasks without retry configuration

4. **`TaskRegistryIntegrationTest`**
   - Tests task auto-registration on startup
   - Tests task lookup
   - Tests retry attempts lookup

5. **`WorkflowExecutionIntegrationTest`**
   - Tests multi-step workflow execution
   - Tests workflow task transitions
   - Tests workflow completion

6. **`ConcurrentExecutionIntegrationTest`**
   - Tests multiple jobs executed concurrently
   - Tests job locking prevents duplicate execution
   - Tests batch processing
   - Tests concurrent job creation

## Running Tests

### Run All Integration Tests

```bash
./gradlew :integration-tests:test
```

### Run Specific Test Class

```bash
./gradlew :integration-tests:test --tests "integration.uk.gov.hmcts.cp.taskmanager.JobCreationAndExecutionIntegrationTest"
```

### Run Tests with Verbose Output

```bash
./gradlew :integration-tests:test --info
```

## Test Database

The integration tests use an **H2 in-memory database** configured in `application-test.properties`. The database schema is managed via Flyway using the same changesets as the main application.

## Configuration

Test-specific configuration:
- **Poll Interval**: 1000ms (faster for tests)
- **Thread Pool**: 2 core, 5 max threads
- **Batch Size**: 10 jobs per poll
- **Database**: H2 in-memory (no persistence between tests)

## Notes

- Tests use `@DirtiesContext` to ensure clean state between tests
- Tests use `Awaitility` for asynchronous assertions
- Test tasks are auto-registered via `TaskRegistry` on application startup
- Each test method runs in isolation with a fresh database

