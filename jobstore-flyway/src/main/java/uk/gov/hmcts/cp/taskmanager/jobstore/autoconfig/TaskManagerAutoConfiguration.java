package uk.gov.hmcts.cp.taskmanager.jobstore.autoconfig;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TaskManagerAutoConfiguration {

    @Bean
    public FlywayConfigurationCustomizer jobstoreFlywayCustomizer() {
        return configuration -> configuration.locations(
                "classpath:db/migration"
        );
    }
}
