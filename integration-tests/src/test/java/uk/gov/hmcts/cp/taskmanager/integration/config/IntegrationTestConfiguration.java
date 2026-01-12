package uk.gov.hmcts.cp.taskmanager.integration.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import uk.gov.hmcts.cp.taskmanager.integration.IntegrationTestApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Meta-annotation for integration tests.
 * Configures Spring Boot test context with test database and properties.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = IntegrationTestApplication.class)
@Import({JacksonTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
public @interface IntegrationTestConfiguration {
}

