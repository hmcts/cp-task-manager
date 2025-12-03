package com.taskmanager.service.task;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used to mark a class as an executable task within the task management system.
 * 
 * <p>Classes implementing {@link ExecutableTask} should be annotated with {@code @Task}
 * to enable automatic registration and discovery by the {@link TaskRegistry}.
 * 
 * <p>The annotation requires a unique task name (value) that identifies the task.
 * This name is used to:
 * <ul>
 *   <li>Register the task in the {@link TaskRegistry}</li>
 *   <li>Associate jobs with their corresponding task implementations</li>
 *   <li>Enable task lookup during job execution</li>
 * </ul>
 * 
 * <p><b>Example usage:</b>
 * <pre>{@code
 * @Task("SWITCH_OVEN_ON")
 * @Component
 * public class SwitchOvenOnTask implements ExecutableTask {
 *     // implementation
 * }
 * }</pre>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see ExecutableTask
 * @see TaskRegistry
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Task {
    /**
     * The unique name identifier for this task.
     * 
     * <p>This value must be unique across all tasks in the system and is used
     * to register and retrieve the task from the {@link TaskRegistry}.
     * 
     * @return the unique task name
     */
    String value();
}