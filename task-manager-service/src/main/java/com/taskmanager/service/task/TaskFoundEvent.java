package com.taskmanager.service.task;

/**
 * Event class representing the discovery of a task class that implements {@link ExecutableTask}.
 * 
 * <p>This class is used to notify the {@link TaskRegistry} about task classes that should be
 * registered. The event contains the {@link Class} object representing the task implementation,
 * which is used to extract the task name from the {@link Task} annotation and register the
 * corresponding task bean.
 * 
 * <p>This event is typically used during application startup when tasks are discovered
 * and registered automatically, or can be used for manual task registration.
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see TaskRegistry
 * @see ExecutableTask
 * @see Task
 */
public class TaskFoundEvent {

    /**
     * The class object representing the task implementation.
     */
    private final Class<?> clazz;

    /**
     * Constructs a new TaskFoundEvent with the specified task class.
     * 
     * @param clazz the class object representing the task implementation that implements
     *              {@link ExecutableTask} and is annotated with {@link Task}
     * @throws NullPointerException if clazz is null
     */
    public TaskFoundEvent(final Class<?> clazz) {
        this.clazz = clazz;
    }

    public Class<?> getClazz() {
        return clazz;
    }
}

