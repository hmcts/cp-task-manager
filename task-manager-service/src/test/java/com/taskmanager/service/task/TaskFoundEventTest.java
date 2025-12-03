package com.taskmanager.service.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskFoundEventTest {

    @Test
    void testConstructorAndGetter() {
        Class<?> testClass = String.class;
        TaskFoundEvent event = new TaskFoundEvent(testClass);
        
        assertNotNull(event);
        assertEquals(testClass, event.getClazz());
    }

    @Test
    void testWithDifferentClasses() {
        TaskFoundEvent event1 = new TaskFoundEvent(String.class);
        TaskFoundEvent event2 = new TaskFoundEvent(Integer.class);
        
        assertEquals(String.class, event1.getClazz());
        assertEquals(Integer.class, event2.getClazz());
        assertNotEquals(event1.getClazz(), event2.getClazz());
    }
}

