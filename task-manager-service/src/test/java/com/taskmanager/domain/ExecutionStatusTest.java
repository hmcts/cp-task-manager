package com.taskmanager.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionStatusTest {

    @Test
    void testExecutionStatusValues() {
        ExecutionStatus[] values = ExecutionStatus.values();
        assertEquals(3, values.length);
        assertEquals(ExecutionStatus.STARTED, values[0]);
        assertEquals(ExecutionStatus.INPROGRESS, values[1]);
        assertEquals(ExecutionStatus.COMPLETED, values[2]);
    }

    @Test
    void testValueOf() {
        assertEquals(ExecutionStatus.STARTED, ExecutionStatus.valueOf("STARTED"));
        assertEquals(ExecutionStatus.INPROGRESS, ExecutionStatus.valueOf("INPROGRESS"));
        assertEquals(ExecutionStatus.COMPLETED, ExecutionStatus.valueOf("COMPLETED"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionStatus.valueOf("INVALID"));
    }
}

