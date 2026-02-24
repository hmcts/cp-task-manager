package uk.gov.hmcts.cp.taskmanager.integration.service;

import static jakarta.json.Json.createObjectBuilder;
import static java.time.OffsetDateTime.now;
import static java.util.Optional.ofNullable;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;
import static uk.gov.hmcts.cp.taskmanager.integration.PostgresIntegrationTestBase.ATTEMPTS_KEY;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.json.JsonObject;
import org.springframework.stereotype.Service;

@Service
public class TaskStatusService {

    private final Map<UUID, TaskStatus> taskStore = new ConcurrentHashMap<>();

    public void recordRetryAttempt(final UUID taskId, final JsonObject jobData) {
        final Optional<TaskStatus> existingTask = getById(taskId);
        if (existingTask.isPresent()) {
            if (existingTask.get().getJobData().containsKey(ATTEMPTS_KEY)) {
                final JsonObject taskJobData = existingTask.get().getJobData();
                updateJobData(existingTask.get().getId(),
                        createObjectBuilder(taskJobData).add(ATTEMPTS_KEY, taskJobData.getInt(ATTEMPTS_KEY) + 1).build());
            }
        } else {
            saveStatus(new TaskStatus(taskId,
                    createObjectBuilder(jobData).add(ATTEMPTS_KEY, 1).build(),
                    INPROGRESS.name(), now()));
        }
    }

    public void saveStatus(final TaskStatus taskStatus) {
        taskStore.put(taskStatus.getId(), taskStatus);
    }

    public Optional<TaskStatus> getById(final UUID taskId) {
        return ofNullable(taskStore.get(taskId));
    }

    public void updateJobData(final UUID taskId, final JsonObject jobData) {
        final Optional<TaskStatus> taskStatusOpt = getById(taskId);
        if (taskStatusOpt.isPresent()) {
            taskStatusOpt.get().setJobData(jobData);
            saveStatus(taskStatusOpt.get());
        }
    }
}
