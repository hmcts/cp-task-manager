package uk.gov.hmcts.cp.taskmanager.autoconfig;

import uk.gov.hmcts.cp.taskmanager.domain.converter.JsonObjectConverter;
import uk.gov.hmcts.cp.taskmanager.domain.executor.JobExecutor;
import uk.gov.hmcts.cp.taskmanager.persistence.TaskManagerPersistenceMarker;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.TaskManagerEntityMarker;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.TaskRegistry;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration
@Import(TaskManagerAutoConfiguration.PersistencePackagesRegistrar.class)
@ConditionalOnClass({EntityManager.class, JpaRepository.class, JpaRepositoryFactory.class, PlatformTransactionManager.class})
public class TaskManagerAutoConfiguration {

    @Bean(name = "jobsRepository")
    @ConditionalOnMissingBean(name = "jobsRepository")
    public JobsRepository jobsRepository(final EntityManager entityManager) {
        final JpaRepositoryFactory jpaRepositoryFactory = new JpaRepositoryFactory(entityManager);
        return jpaRepositoryFactory.getRepository(JobsRepository.class);
    }

    @Bean
    @ConditionalOnMissingBean(JsonObjectConverter.class)
    public JsonObjectConverter jsonObjectConverter() {
        return new JsonObjectConverter();
    }

    @Bean
    @ConditionalOnMissingBean(JobService.class)
    public JobService jobService(
            final JobsRepository jobsRepository,
            final JsonObjectConverter jsonObjectConverter
    ) {
        return new JobService(jobsRepository, jsonObjectConverter);
    }

    @Bean
    @ConditionalOnMissingBean(TaskRegistry.class)
    public TaskRegistry taskRegistry(final ObjectProvider<ExecutableTask> executableTaskProvider) {
        return new TaskRegistry(executableTaskProvider);
    }

    @Bean
    @ConditionalOnMissingBean(JobExecutor.class)
    public JobExecutor jobExecutor(
            final JobService jobService,
            final TaskRegistry taskRegistry,
            final PlatformTransactionManager platformTransactionManager
    ) {
        return new JobExecutor(jobService, taskRegistry, platformTransactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(ExecutionService.class)
    public ExecutionService executionService(final JobService jobService, final TaskRegistry taskRegistry) {
        return new ExecutionService(jobService, taskRegistry);
    }

    static final class PersistencePackagesRegistrar implements ImportBeanDefinitionRegistrar {
        @Override
        public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
            // registers base packages Boot uses for scanning defaults
            AutoConfigurationPackages.register(registry,
                    TaskManagerPersistenceMarker.class.getPackageName());

            // registers packages Boot uses specifically for JPA entity scanning
            EntityScanPackages.register(registry,
                    TaskManagerEntityMarker.class.getPackageName());
        }
    }
}
