package com.taskmanager.service.task;


import com.taskmanager.service.task.ExecutableTask;
import com.taskmanager.service.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import  java.util.List;
import jakarta.annotation.PostConstruct;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Central registry for managing and retrieving executable tasks.
 * 
 * <p>The TaskRegistry maintains a map of task names to their corresponding
 * {@link ExecutableTask} implementations. It provides automatic discovery and
 * registration of tasks annotated with {@link Task} during application startup.
 * 
 * <p>Tasks are automatically discovered via Spring's {@link ObjectProvider} which
 * collects all beans implementing {@link ExecutableTask}. The registry extracts
 * the task name from the {@link Task} annotation and stores the task proxy for
 * later retrieval.
 * 
 * <p>The registry supports:
 * <ul>
 *   <li>Automatic task discovery and registration on startup</li>
 *   <li>Manual task registration via {@link TaskFoundEvent}</li>
 *   <li>Task lookup by name</li>
 *   <li>Retry configuration lookup</li>
 * </ul>
 * 
 * <p>This registry is used by:
 * <ul>
 *   <li>{@link com.taskmanager.domain.executor.TaskExecutor} - to retrieve tasks for execution</li>
 *   <li>{@link com.taskmanager.service.ExecutionService} - to determine retry configuration</li>
 * </ul>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see ExecutableTask
 * @see Task
 * @see TaskFoundEvent
 */
@Component
public class TaskRegistry {

    /**
     * Logger for this class.
     */
    private final Logger logger = LoggerFactory.getLogger(TaskRegistry.class);

    /**
     * Map of task names to their ExecutableTask implementations.
     * Key is the task name from {@link Task#value()}, value is the task proxy.
     */
    private final Map<String, ExecutableTask> taskProxyByNameMap = new HashMap<>();

    /**
     * Spring ObjectProvider for accessing all ExecutableTask beans.
     * Used for automatic task discovery.
     */
    @Autowired
    private ObjectProvider<ExecutableTask> taskBeanProxy;
    
    /**
     * Automatically discovers and registers all tasks annotated with {@link Task}.
     * 
     * <p>This method is called after dependency injection completes (via {@link PostConstruct}).
     * It iterates through all {@link ExecutableTask} beans provided by Spring, extracts
     * the task name from the {@link Task} annotation, and registers them in the registry.
     * 
     * <p>Tasks without the {@link Task} annotation are skipped. If multiple tasks have
     * the same name, only the first one is registered (using {@link Map#putIfAbsent}).
     */
    @PostConstruct
    public void autoRegisterTasks() {
        logger.info("Auto-discovering and registering tasks...");
        for (final ExecutableTask taskProxy : taskBeanProxy) {
            // Get the actual class (not the proxy class)
            final Class<?> actualClass = AopUtils.getTargetClass(taskProxy);
            final Task taskAnnotation = actualClass.getAnnotation(Task.class);
            
            if (taskAnnotation != null) {
                final String taskName = taskAnnotation.value();
                taskProxyByNameMap.putIfAbsent(taskName, taskProxy);
                logger.info("Auto-registered task [type={}], [name={}]", actualClass, taskName);
            } else {
                logger.debug("Skipping ExecutableTask without @Task annotation: {}", actualClass);
            }
        }
        logger.info("Auto-registration complete. Registered {} task(s)", taskProxyByNameMap.size());
    }

    /**
     * Manually registers a task based on a TaskFoundEvent.
     * 
     * <p>This method looks up the task bean corresponding to the class in the event,
     * extracts the task name from the {@link Task} annotation, and registers it.
     * 
     * <p>This method is typically used for manual task registration or when tasks
     * are discovered dynamically. If the task is not found in the Spring context,
     * an error is logged.
     * 
     * @param event the TaskFoundEvent containing the task class to register
     * @throws NullPointerException if the task class does not have a {@link Task} annotation
     */
    public void register(final TaskFoundEvent event) {

        final Class taskClass = event.getClazz();
        final String taskName = ((Task) taskClass.getAnnotation(Task.class)).value();

        logger.info("Notified of Work Task [type={}], [name={}]", taskClass, taskName);

        for (final ExecutableTask taskProxy : taskBeanProxy) {
            // Get the actual class (not the proxy class)
            final Class<?> actualClass = AopUtils.getTargetClass(taskProxy);
            if (actualClass.equals(taskClass)) {
                taskProxyByNameMap.putIfAbsent(taskName, taskProxy);
                logger.info("Registering Work Task proxy [type={}], [name={}]", taskProxy, taskName);
                break;
            }
        }

        if (taskProxyByNameMap.get(taskName) == null) {
            logger.error("No Injected proxy class provided for task [{}]", taskClass);
        }

    }

    /**
     * Retrieves a task by its name.
     * 
     * @param taskName the task name (from {@link Task#value()})
     * @return an Optional containing the ExecutableTask if found, empty otherwise
     */
    public Optional<ExecutableTask> getTask(final String taskName) {
        return Optional.ofNullable(taskProxyByNameMap.get(taskName));
    }

    /**
     * Finds the number of retry attempts configured for a task.
     * 
     * <p>This method looks up the task and returns the number of retry durations
     * configured. The number of retry attempts equals the size of the retry durations list.
     * 
     * <p>If the task is not found or does not have retry configuration, returns 0.
     * 
     * @param taskName the task name
     * @return the number of retry attempts configured, or 0 if not found or not configured
     */
    public Integer findRetryAttemptsRemainingFor(final String taskName) {
        return getTask(taskName)
                .map(this::findRetryAttemptsRemainingFor)
                .orElse(0);
    }

    /**
     * Finds the number of retry attempts for a specific task instance.
     * 
     * <p>This method extracts the retry durations list from the task and returns its size.
     * If the task does not have retry configuration, returns 0.
     * 
     * @param task the ExecutableTask instance
     * @return the number of retry attempts (size of retry durations list), or 0 if not configured
     */
    private Integer findRetryAttemptsRemainingFor(final ExecutableTask task) {
        return task.getRetryDurationsInSecs()
                .map(List::size)
                .orElse(0);
    }
}

