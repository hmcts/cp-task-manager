package com.taskmanager.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot application for integration tests.
 * Enables scheduling for JobExecutor and component scanning.
 */
@SpringBootApplication(scanBasePackages = "com.taskmanager")
@EnableScheduling
public class IntegrationTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApplication.class, args);
    }
}

