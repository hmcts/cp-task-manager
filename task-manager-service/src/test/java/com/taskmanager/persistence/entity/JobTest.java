package com.taskmanager.persistence.entity;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobTest {

    private JsonObject testJobData;
    private UUID testJobId;
    private UUID testWorkerId;
    private ZonedDateTime testStartTime;
    private ZonedDateTime testLockTime;

    @BeforeEach
    void setUp() {
        testJobData = Json.createObjectBuilder()
                .add("key", "value")
                .build();
        testJobId = UUID.randomUUID();
        testWorkerId = UUID.randomUUID();
        testStartTime = ZonedDateTime.now();
        testLockTime = ZonedDateTime.now();
    }

    @Test
    void testNoArgsConstructor() {
        Job job = new Job();
        assertNotNull(job);
    }

    @Test
    void testAllArgsConstructor() {
        Job job = new Job(testJobId, testWorkerId, testLockTime, "TEST_TASK", 
                testStartTime, testJobData, 5, 3);

        assertEquals(testJobId, job.getJobId());
        assertEquals(testWorkerId, job.getWorkerId());
        assertEquals(testLockTime, job.getWorkerLockTime());
        assertEquals("TEST_TASK", job.getAssignedTaskName());
        assertEquals(testStartTime, job.getAssignedTaskStartTime());
        assertEquals(testJobData, job.getJobData());
        assertEquals(5, job.getRetryAttemptsRemaining());
        assertEquals(3, job.getPriority());
    }

    @Test
    void testConstructorWithSevenParameters() {
        Job job = new Job(testJobId, testJobData, "TEST_TASK", testStartTime, 
                testWorkerId, testLockTime, 5);

        assertEquals(testJobId, job.getJobId());
        assertEquals(testJobData, job.getJobData());
        assertEquals("TEST_TASK", job.getAssignedTaskName());
        assertEquals(testStartTime, job.getAssignedTaskStartTime());
        assertEquals(testWorkerId, job.getWorkerId());
        assertEquals(testLockTime, job.getWorkerLockTime());
        assertEquals(5, job.getRetryAttemptsRemaining());
        assertEquals(10, job.getPriority()); // Default priority
    }

    @Test
    void testConstructorWithEightParameters() {
        Job job = new Job(testJobId, testJobData, "TEST_TASK", testStartTime, 
                testWorkerId, testLockTime, 5, 7);

        assertEquals(testJobId, job.getJobId());
        assertEquals(testJobData, job.getJobData());
        assertEquals("TEST_TASK", job.getAssignedTaskName());
        assertEquals(testStartTime, job.getAssignedTaskStartTime());
        assertEquals(testWorkerId, job.getWorkerId());
        assertEquals(testLockTime, job.getWorkerLockTime());
        assertEquals(5, job.getRetryAttemptsRemaining());
        assertEquals(7, job.getPriority());
    }

    @Test
    void testConstructorWithNullPriority() {
        Job job = new Job(testJobId, testJobData, "TEST_TASK", testStartTime, 
                testWorkerId, testLockTime, 5, null);

        assertEquals(10, job.getPriority()); // Default when null
    }

    @Test
    void testPrePersistGeneratesJobId() {
        Job job = new Job();
        job.setAssignedTaskName("TEST_TASK");
        job.setAssignedTaskStartTime(ZonedDateTime.now());
        job.setRetryAttemptsRemaining(0);
        job.setPriority(5);

        assertNull(job.getJobId());
        job.onCreate();
        assertNotNull(job.getJobId());
    }

    @Test
    void testPrePersistDoesNotOverrideExistingJobId() {
        Job job = new Job();
        job.setJobId(testJobId);
        job.setAssignedTaskName("TEST_TASK");
        job.setAssignedTaskStartTime(ZonedDateTime.now());
        job.setRetryAttemptsRemaining(0);
        job.setPriority(5);

        job.onCreate();
        assertEquals(testJobId, job.getJobId());
    }

    @Test
    void testPrePersistSetsDefaultPriority() {
        Job job = new Job();
        job.setJobId(testJobId);
        job.setAssignedTaskName("TEST_TASK");
        job.setAssignedTaskStartTime(ZonedDateTime.now());
        job.setRetryAttemptsRemaining(0);
        job.setPriority(0); // Set to 0 to trigger default

        job.onCreate();
        assertEquals(10, job.getPriority()); // Should be set to default
    }

    @Test
    void testPrePersistDoesNotOverrideExistingPriority() {
        Job job = new Job();
        job.setJobId(testJobId);
        job.setAssignedTaskName("TEST_TASK");
        job.setAssignedTaskStartTime(ZonedDateTime.now());
        job.setRetryAttemptsRemaining(0);
        job.setPriority(5);

        job.onCreate();
        assertEquals(5, job.getPriority()); // Should remain unchanged
    }

    @Test
    void testDefaultPriority() {
        Job job = new Job();
        assertEquals(10, job.getPriority()); // Default value
    }

    @Test
    void testSettersAndGetters() {
        Job job = new Job();
        job.setJobId(testJobId);
        job.setWorkerId(testWorkerId);
        job.setWorkerLockTime(testLockTime);
        job.setAssignedTaskName("TEST_TASK");
        job.setAssignedTaskStartTime(testStartTime);
        job.setJobData(testJobData);
        job.setRetryAttemptsRemaining(3);
        job.setPriority(2);

        assertEquals(testJobId, job.getJobId());
        assertEquals(testWorkerId, job.getWorkerId());
        assertEquals(testLockTime, job.getWorkerLockTime());
        assertEquals("TEST_TASK", job.getAssignedTaskName());
        assertEquals(testStartTime, job.getAssignedTaskStartTime());
        assertEquals(testJobData, job.getJobData());
        assertEquals(3, job.getRetryAttemptsRemaining());
        assertEquals(2, job.getPriority());
    }
}

