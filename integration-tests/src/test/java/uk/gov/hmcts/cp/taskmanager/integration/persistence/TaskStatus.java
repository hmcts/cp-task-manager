package uk.gov.hmcts.cp.taskmanager.integration.persistence;

import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "task_status")
public class TaskStatus {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "job_data", columnDefinition = "TEXT")
    @Convert(converter = JsonObjectConverter.class)
    private JsonObject jobData;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

}