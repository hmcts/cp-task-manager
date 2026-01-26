package uk.gov.hmcts.cp.taskmanager.autoconfig;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(
        beforeName = {
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
        }
)
@ConditionalOnClass({EntityManager.class, EntityScanPackages.class})
public class TaskManagerEntityPackagesAutoConfiguration {

    private static final String TASK_MANAGER_ENTITY_PACKAGE = "uk.gov.hmcts.cp.taskmanager.persistence.entity";

    @Bean
    public static BeanFactoryPostProcessor taskManagerEntityPackagesRegistrar() {
        return beanFactory -> {
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                return;
            }

            Set<String> packages = new LinkedHashSet<>();

            // 1) If consumer already configured entity scan packages, keep them
            packages.addAll(EntityScanPackages.get(beanFactory).getPackageNames());

            // 2) Otherwise fall back to the consumer application's base packages
            if (packages.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
                packages.addAll(AutoConfigurationPackages.get(beanFactory));
            }

            // 3) Always add library entities
            packages.add(TASK_MANAGER_ENTITY_PACKAGE);

            // Register ONCE with the union (do not call register twice)
            EntityScanPackages.register(registry, packages);
        };
    }
}
