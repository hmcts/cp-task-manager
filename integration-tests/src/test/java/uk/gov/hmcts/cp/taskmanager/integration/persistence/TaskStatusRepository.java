package uk.gov.hmcts.cp.taskmanager.integration.persistence;


import java.util.UUID;

import jakarta.json.JsonObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskStatusRepository extends JpaRepository<TaskStatus, UUID> {

    @Modifying
    @Query(value = "UPDATE task_status SET status = :status WHERE id = :id", nativeQuery = true)
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE task_status SET job_data = :jobData WHERE id = :id", nativeQuery = true)
    void updateJobData(@Param("id") UUID id, @Param("jobData") String jobData);
}