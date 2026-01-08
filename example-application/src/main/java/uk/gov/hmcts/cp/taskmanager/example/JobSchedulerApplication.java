package uk.gov.hmcts.cp.taskmanager.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"uk.gov.hmcts.cp.taskmanager"})
@EnableAsync
@EnableJpaRepositories(basePackages = "uk.gov.hmcts.cp.taskmanager.persistence.repository")
@EntityScan(basePackages = "uk.gov.hmcts.cp.taskmanager.persistence.entity")
public class JobSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobSchedulerApplication.class, args);
    }
}