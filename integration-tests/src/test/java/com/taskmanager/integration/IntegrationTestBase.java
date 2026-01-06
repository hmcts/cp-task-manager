package com.taskmanager.integration;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    @Autowired
    private SpringLiquibase liquibase;

    @BeforeAll
    void migrateDatabase() throws Exception {
        liquibase.afterPropertiesSet();
    }
}