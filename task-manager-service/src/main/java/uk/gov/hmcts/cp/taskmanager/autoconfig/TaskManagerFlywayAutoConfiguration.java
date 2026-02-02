package uk.gov.hmcts.cp.taskmanager.autoconfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
@ConditionalOnClass(name = "org.flywaydb.core.Flyway")
@EnableConfigurationProperties(TaskManagerSchemaProperties.class)
public class TaskManagerFlywayAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "taskmanager.schema", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FlywayConfigurationCustomizer taskManagerFlywayCustomizer(
            final TaskManagerSchemaProperties taskManagerSchemaProperties,
            final FlywayProperties flywayProperties
    ) {
        return (final var fluentConfiguration) -> {
            // Merge client's locations + ours (even if the client overrides spring.flyway.locations)
            final Set<String> mergedLocations = new LinkedHashSet<>();
            final List<String> existingLocations = flywayProperties.getLocations();
            if (existingLocations != null) {
                mergedLocations.addAll(existingLocations);
            }

            mergedLocations.add(taskManagerSchemaProperties.getLocation()); // e.g. classpath:db/taskmanager

            fluentConfiguration.locations(mergedLocations.toArray(String[]::new));
        };
    }
}
