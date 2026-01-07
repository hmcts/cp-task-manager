package com.taskmanager.integration;

import com.taskmanager.integration.config.IntegrationTestConfiguration;
import com.taskmanager.integration.tasks.TestCompletedTask;
import com.taskmanager.integration.tasks.TestRetryTask;
import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.TaskRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TaskRegistry auto-registration.
 * Tests that tasks are automatically discovered and registered on application startup.
 */
@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskRegistryIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private TaskRegistry taskRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testTaskAutoRegistration() {
        // Given/When - Tasks should be auto-registered on application startup
        
        // Then - Test tasks should be registered
        final Optional<ExecutableTask> completedTask = taskRegistry.getTask("TEST_COMPLETED_TASK");
        assertThat(completedTask).isPresent();
        assertThat(completedTask.get()).isInstanceOf(TestCompletedTask.class);

        final Optional<ExecutableTask> retryTask = taskRegistry.getTask("TEST_RETRY_TASK");
        assertThat(retryTask).isPresent();
        assertThat(retryTask.get()).isInstanceOf(TestRetryTask.class);
    }

    @Test
    void testTaskNotFound() {
        // Given/When - Query for non-existent task
        final Optional<ExecutableTask> task = taskRegistry.getTask("NON_EXISTENT_TASK");

        // Then - Should return empty
        assertThat(task).isEmpty();
    }

    @Test
    void testRetryAttemptsLookup() {
        // Given - Task with retry configuration
        // When
        final Integer retryAttempts = taskRegistry.findRetryAttemptsRemainingFor("TEST_RETRY_TASK");

        // Then - Should return number of retry attempts (3)
        assertThat(retryAttempts).isEqualTo(3);
    }

    @Test
    void testRetryAttemptsLookupForTaskWithoutRetry() {
        // Given - Task without retry configuration
        // When
        final Integer retryAttempts = taskRegistry.findRetryAttemptsRemainingFor("TEST_COMPLETED_TASK");

        // Then - Should return 0
        assertThat(retryAttempts).isEqualTo(0);
    }

    @Test
    void testRetryAttemptsLookupForNonExistentTask() {
        // Given - Non-existent task
        // When
        final Integer retryAttempts = taskRegistry.findRetryAttemptsRemainingFor("NON_EXISTENT_TASK");

        // Then - Should return 0
        assertThat(retryAttempts).isEqualTo(0);
    }
}

