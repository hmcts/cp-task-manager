package uk.gov.hmcts.cp.taskmanager.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application for integration tests.
 * Enables scheduling for JobExecutor and component scanning.
 */
@SpringBootApplication(scanBasePackages = "uk.gov.hmcts.cp.taskmanager")
@EnableScheduling
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.cp.taskmanager.persistence.repository")
@EntityScan(basePackages = "uk.gov.hmcts.cp.taskmanager.persistence.entity")
public class IntegrationTestApplication {
}

