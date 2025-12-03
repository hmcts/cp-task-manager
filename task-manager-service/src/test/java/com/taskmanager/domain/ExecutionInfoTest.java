package com.taskmanager.domain;

import com.taskmanager.persistence.entity.Job;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionInfoTest {

    private JsonObject createTestJsonObject() {
        return Json.createObjectBuilder()
                .add("key", "value")
                .build();
    }

    @Test
    void testConstructorWithFourParameters() {
        JsonObject jobData = createTestJsonObject();
        String assignedTaskName = "TEST_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        ExecutionStatus status = ExecutionStatus.STARTED;

        ExecutionInfo info = new ExecutionInfo(jobData, assignedTaskName, startTime, status);

        assertEquals(jobData, info.getJobData());
        assertEquals(assignedTaskName, info.getAssignedTaskName());
        assertEquals(startTime, info.getAssignedTaskStartTime());
        assertEquals(status, info.getExecutionStatus());
        assertFalse(info.isShouldRetry());
        assertNull(info.getPriority());
    }

    @Test
    void testConstructorWithFiveParameters() {
        JsonObject jobData = createTestJsonObject();
        String assignedTaskName = "TEST_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        ExecutionStatus status = ExecutionStatus.INPROGRESS;
        boolean shouldRetry = true;

        ExecutionInfo info = new ExecutionInfo(jobData, assignedTaskName, startTime, status, shouldRetry);

        assertEquals(jobData, info.getJobData());
        assertEquals(assignedTaskName, info.getAssignedTaskName());
        assertEquals(startTime, info.getAssignedTaskStartTime());
        assertEquals(status, info.getExecutionStatus());
        assertTrue(info.isShouldRetry());
        assertNull(info.getPriority());
    }

    @Test
    void testConstructorWithSixParameters() {
        JsonObject jobData = createTestJsonObject();
        String assignedTaskName = "TEST_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        ExecutionStatus status = ExecutionStatus.COMPLETED;
        boolean shouldRetry = false;
        Integer priority = 5;

        ExecutionInfo info = new ExecutionInfo(jobData, assignedTaskName, startTime, status, shouldRetry, priority);

        assertEquals(jobData, info.getJobData());
        assertEquals(assignedTaskName, info.getAssignedTaskName());
        assertEquals(startTime, info.getAssignedTaskStartTime());
        assertEquals(status, info.getExecutionStatus());
        assertFalse(info.isShouldRetry());
        assertEquals(priority, info.getPriority());
    }

    @Test
    void testBuilderCreation() {
        ExecutionInfo.Builder builder = ExecutionInfo.executionInfo();
        assertNotNull(builder);
    }

    @Test
    void testBuilderWithAllFields() {
        JsonObject jobData = createTestJsonObject();
        String assignedTaskName = "TEST_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        ExecutionStatus status = ExecutionStatus.STARTED;
        boolean shouldRetry = true;
        Integer priority = 3;

        ExecutionInfo info = ExecutionInfo.executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(assignedTaskName)
                .withAssignedTaskStartTime(startTime)
                .withExecutionStatus(status)
                .withShouldRetry(shouldRetry)
                .withPriority(priority)
                .build();

        assertEquals(jobData, info.getJobData());
        assertEquals(assignedTaskName, info.getAssignedTaskName());
        assertEquals(startTime, info.getAssignedTaskStartTime());
        assertEquals(status, info.getExecutionStatus());
        assertTrue(info.isShouldRetry());
        assertEquals(priority, info.getPriority());
    }

    @Test
    void testBuilderFromExecutionInfo() {
        JsonObject jobData = createTestJsonObject();
        ExecutionInfo original = new ExecutionInfo(jobData, "ORIGINAL_TASK", 
                ZonedDateTime.now(), ExecutionStatus.STARTED, true, 2);

        ExecutionInfo copy = ExecutionInfo.executionInfo()
                .from(original)
                .withAssignedTaskName("NEW_TASK")
                .build();

        assertEquals(jobData, copy.getJobData());
        assertEquals("NEW_TASK", copy.getAssignedTaskName());
        assertEquals(original.getAssignedTaskStartTime(), copy.getAssignedTaskStartTime());
        assertEquals(ExecutionStatus.STARTED, copy.getExecutionStatus());
        assertTrue(copy.isShouldRetry());
        assertEquals(2, copy.getPriority());
    }

    @Test
    void testBuilderFromJob() {
        UUID jobId = UUID.randomUUID();
        JsonObject jobData = createTestJsonObject();
        String assignedTaskName = "JOB_TASK";
        ZonedDateTime startTime = ZonedDateTime.now();
        int priority = 7;

        Job job = new Job(jobId, jobData, assignedTaskName, startTime, null, null, 5, priority);

        ExecutionInfo info = ExecutionInfo.executionInfo()
                .fromJob(job)
                .build();

        assertEquals(jobData, info.getJobData());
        assertEquals(assignedTaskName, info.getAssignedTaskName());
        assertEquals(startTime, info.getAssignedTaskStartTime());
        assertEquals(ExecutionStatus.STARTED, info.getExecutionStatus());
        assertEquals(priority, info.getPriority());
    }

    @Test
    void testBuilderRetryValidation() {
        ExecutionInfo.Builder builder = ExecutionInfo.executionInfo()
                .withShouldRetry(true)
                .withJobData(null)
                .withAssignedTaskName(null)
                .withAssignedTaskStartTime(null);

        RuntimeException exception = assertThrows(RuntimeException.class, builder::build);
        assertTrue(exception.getMessage().contains("retry exhaust task details"));
    }

    @Test
    void testBuilderRetryValidationWithNullJobData() {
        ExecutionInfo.Builder builder = ExecutionInfo.executionInfo()
                .withShouldRetry(true)
                .withJobData(null)
                .withAssignedTaskName("TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now());

        RuntimeException exception = assertThrows(RuntimeException.class, builder::build);
        assertTrue(exception.getMessage().contains("retry exhaust task details"));
    }

    @Test
    void testBuilderRetryValidationWithNullTask() {
        ExecutionInfo.Builder builder = ExecutionInfo.executionInfo()
                .withShouldRetry(true)
                .withJobData(createTestJsonObject())
                .withAssignedTaskName(null)
                .withAssignedTaskStartTime(ZonedDateTime.now());

        RuntimeException exception = assertThrows(RuntimeException.class, builder::build);
        assertTrue(exception.getMessage().contains("retry exhaust task details"));
    }

    @Test
    void testBuilderRetryValidationWithNullStartTime() {
        ExecutionInfo.Builder builder = ExecutionInfo.executionInfo()
                .withShouldRetry(true)
                .withJobData(createTestJsonObject())
                .withAssignedTaskName("TASK")
                .withAssignedTaskStartTime(null);

        RuntimeException exception = assertThrows(RuntimeException.class, builder::build);
        assertTrue(exception.getMessage().contains("retry exhaust task details"));
    }

    @Test
    void testBuilderRetryWithValidData() {
        JsonObject jobData = createTestJsonObject();
        ExecutionInfo info = ExecutionInfo.executionInfo()
                .withShouldRetry(true)
                .withJobData(jobData)
                .withAssignedTaskName("TASK")
                .withAssignedTaskStartTime(ZonedDateTime.now())
                .build();

        assertNotNull(info);
        assertTrue(info.isShouldRetry());
    }

    @Test
    void testBuilderWithoutRetryCanHaveNullFields() {
        ExecutionInfo info = ExecutionInfo.executionInfo()
                .withJobData(null)
                .withAssignedTaskName(null)
                .withAssignedTaskStartTime(null)
                .build();

        assertNotNull(info);
        assertNull(info.getJobData());
        assertNull(info.getAssignedTaskName());
        assertNull(info.getAssignedTaskStartTime());
    }
}

