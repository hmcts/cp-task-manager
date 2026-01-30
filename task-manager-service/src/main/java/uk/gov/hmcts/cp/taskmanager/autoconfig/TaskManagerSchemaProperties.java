package uk.gov.hmcts.cp.taskmanager.autoconfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "taskmanager.schema")
public class TaskManagerSchemaProperties {

    /**
     * If false, the library will not add its migrations to Flyway.
     */
    private boolean enabled = true;

    /**
     * Where this library’s migrations live inside the jar.
     */
    private String location = "classpath:db/taskmanager";
}
