package uk.gov.hmcts.cp.taskmanager.autoconfig;

import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.executor.JobExecutor;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.TaskRegistry;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@EntityScan(basePackageClasses = Job.class)
public class TaskManagerAutoConfiguration {

    @Bean
    public ObjectProvider<ExecutableTask> taskBeanProxy(final ApplicationContext ctx) {
        return ctx.getBeanProvider(ExecutableTask.class);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobsRepository jobsRepository(final EntityManager entityManager) {
        JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
        return factory.getRepository(JobsRepository.class);
    }

    @Bean
    public JsonObjectConverter jsonObjectConverter() {
        return new JsonObjectConverter();
    }

    @Bean
    public JobService jobService(final JobsRepository jobsRepository, final JsonObjectConverter jsonObjectConverter) {
        return new JobService(jobsRepository, jsonObjectConverter);
    }

    @Bean
    public JobExecutor jobExecutor(final JobService jobService, final TaskRegistry taskRegistry,
                                   final PlatformTransactionManager platformTransactionManager) {
        return new JobExecutor(jobService, taskRegistry, platformTransactionManager);
    }

    @Bean
    public TaskRegistry taskRegistry(final ObjectProvider<ExecutableTask> taskBeanProxy) {
        return new TaskRegistry(taskBeanProxy);
    }

    @Bean
    public ExecutionService executionService(final JobService jobService, final TaskRegistry taskRegistry) {
        return new ExecutionService(jobService, taskRegistry);
    }
}
