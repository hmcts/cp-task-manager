package uk.gov.hmcts.cp.taskmanager.integration.persistence;

import static jakarta.json.Json.createObjectBuilder;
import static java.time.OffsetDateTime.now;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;
import static uk.gov.hmcts.cp.taskmanager.integration.PostgresIntegrationTestBase.ATTEMPTS_KEY;

import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskStatusService {

    private final TaskStatusRepository taskStatusRepository;

    @Autowired
    public TaskStatusService(final TaskStatusRepository taskStatusRepository) {
        this.taskStatusRepository = taskStatusRepository;
    }

    public void recordRetryAttempt(final UUID taskId, final JsonObject jobData) {
        final Optional<TaskStatus> existingTask = findById(taskId);
        if (existingTask.isPresent()) {
            if (existingTask.get().getJobData().containsKey(ATTEMPTS_KEY)) {
                final JsonObject taskJobData = existingTask.get().getJobData();
                updateJobData(existingTask.get().getId(),
                        createObjectBuilder(taskJobData).add(ATTEMPTS_KEY, taskJobData.getInt(ATTEMPTS_KEY) + 1).build());
            }
        } else {
            insertTaskStatus(new TaskStatus(taskId,
                    createObjectBuilder(jobData).add(ATTEMPTS_KEY, 1).build(),
                    INPROGRESS.name(), now()));
        }
    }
    @Transactional
    public void insertTaskStatus(final TaskStatus taskStatus) {
        taskStatusRepository.save(taskStatus);
    }

    @Transactional
    public Optional<TaskStatus> findById(final UUID taskId) {
        return taskStatusRepository.findById(taskId);
    }

    @Transactional
    public void updateTaskStatus(final UUID id, final String taskStatus) {
        taskStatusRepository.updateStatus(id, taskStatus);
    }

    @Transactional
    public void updateJobData(final UUID id, final JsonObject jobData) {
        taskStatusRepository.updateJobData(id, jobData.toString());
    }
}
