package com.taskmanager.integration.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class JacksonTestConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}