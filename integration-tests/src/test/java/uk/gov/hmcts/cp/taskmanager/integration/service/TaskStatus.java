package uk.gov.hmcts.cp.taskmanager.integration.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskStatus {
    private UUID id;
    private JsonObject jobData;
    private String status;
    private OffsetDateTime createdAt;
}