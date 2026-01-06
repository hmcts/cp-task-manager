package com.taskmanager.integration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application for integration tests.
 * Enables scheduling for JobExecutor and component scanning.
 */
@SpringBootApplication(scanBasePackages = "com.taskmanager")
@EnableScheduling
@EnableJpaRepositories(basePackages = "com.taskmanager.persistence.repository")
@EntityScan(basePackages = "com.taskmanager.persistence.entity")
public class IntegrationTestApplication {
}

