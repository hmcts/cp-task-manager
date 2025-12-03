package com.taskmanager.service.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TaskRegistryTest {

    private TaskRegistry taskRegistry;

    @BeforeEach
    void setUp() {
        taskRegistry = new TaskRegistry();
    }

    @Test
    void testGetTaskNotFound() {
        Optional<ExecutableTask> result = taskRegistry.getTask("NON_EXISTENT_TASK");
        assertFalse(result.isPresent());
    }

    @Test
    void testFindRetryAttemptsRemainingForTaskNotFound() {
        Integer retryAttempts = taskRegistry.findRetryAttemptsRemainingFor("NON_EXISTENT");
        assertEquals(0, retryAttempts);
    }

    @Test
    void testRegisterWithNullTask() {
        TaskFoundEvent event = new TaskFoundEvent(String.class);
        // The register method will throw NullPointerException when trying to get annotation from String.class
        // This is expected behavior since String.class doesn't have @Task annotation
        assertThrows(NullPointerException.class, () -> taskRegistry.register(event));
        
        // Registry should still be functional
        Optional<ExecutableTask> result = taskRegistry.getTask("NON_EXISTENT");
        assertFalse(result.isPresent());
    }
}
