package uk.gov.hmcts.cp.taskmanager.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application for integration tests.
 * Enables scheduling for JobExecutor and component scanning.
 */
@SpringBootApplication(scanBasePackages = "uk.gov.hmcts.cp.taskmanager.integration")
@EnableScheduling
public class IntegrationTestApplication {
}

