package com.taskmanager.config;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Configuration to ensure Liquibase runs on application startup.
 * This is a fallback in case Spring Boot auto-configuration doesn't work.
 */
@Configuration
public class LiquibaseConfig {

    @Bean
    public CommandLineRunner liquibaseRunner(DataSource dataSource,
                                              @Value("${spring.liquibase.change-log}") String changelog) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                
                Liquibase liquibase = new Liquibase(
                        changelog.replace("classpath:", ""),
                        new ClassLoaderResourceAccessor(),
                        database
                );
                
                liquibase.update("");
                System.out.println("Liquibase migrations completed successfully");
            } catch (Exception e) {
                System.err.println("Liquibase migration failed: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
        };
    }
}

