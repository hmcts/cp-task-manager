package uk.gov.hmcts.cp.taskmanager.integration.persistence;

import java.util.UUID;

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

    @Transactional
    public void insertTaskStatus(final TaskStatus taskStatus) {
        taskStatusRepository.save(taskStatus);
    }

    @Transactional
    public void updateTaskStatus(final UUID id, final String taskStatus) {
        taskStatusRepository.updateStatus(id, taskStatus);
    }
}
