package uk.gov.hmcts.cp.taskmanager.integration.config;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class LiquibaseTestConfig {

    @Bean
    public SpringLiquibase liquibase(@Qualifier("dataSource") DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:liquibase/jobstore-db-changelog.xml");
        liquibase.setShouldRun(true);
        return liquibase;
    }
}
