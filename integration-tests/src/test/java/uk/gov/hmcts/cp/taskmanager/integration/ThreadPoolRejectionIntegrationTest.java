package uk.gov.hmcts.cp.taskmanager.integration;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.domain.executor.JobExecutor;
import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import uk.gov.hmcts.cp.taskmanager.service.task.TaskRegistry;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import jakarta.json.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Reproduces the production incident where the job executor's thread pool rejected a
 * submission ("ExecutorService in active state did not accept task", a
 * {@link RejectedExecutionException} wrapped as {@link TaskRejectedException}) and verifies
 * a job caught by that failure is released (worker_id cleared) rather than left permanently
 * locked to a worker that never ran it.
 */
@IntegrationTestConfiguration
@Import(ThreadPoolRejectionIntegrationTest.RejectingExecutorTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ThreadPoolRejectionIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobsRepository jobsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
    }

    @Test
    void jobIsReleasedNotStuckWhenThreadPoolRejectsExecution() {
        // Given - a due job, backed by the real JobExecutor bean whose thread pool
        // (see RejectingExecutorTestConfig) always rejects submissions.
        final ExecutionInfo executionInfo = new ExecutionInfo(
                Json.createObjectBuilder().add("test", "data").build(),
                "TEST_COMPLETED_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        // When - JobExecutor's scheduled poll claims the job (assigning a workerId), then
        // fails to submit it to the thread pool.
        // Then - the job must be released (worker_id cleared), not left permanently locked.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            final List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).hasSize(1);
            assertThat(jobs.get(0).getWorkerId()).isNull();
        });

        // And - it stays released across further poll cycles rather than getting stuck
        // mid-assignment on a later attempt.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() -> {
            final List<Job> jobs = jobsRepository.findAll();
            assertThat(jobs).hasSize(1);
            assertThat(jobs.get(0).getWorkerId()).isNull();
        });
    }

    @TestConfiguration
    static class RejectingExecutorTestConfig {

        /**
         * Overrides the auto-configured {@code JobExecutor} bean (via
         * {@code @ConditionalOnMissingBean}) with one backed by an executor that always
         * rejects submissions, while still reporting healthy pool capacity - simulating the
         * real-world race where pool state changes between JobExecutor's capacity check and
         * its actual submission.
         */
        @Bean
        JobExecutor jobExecutor(final JobService jobService,
                                final TaskRegistry taskRegistry,
                                final PlatformTransactionManager transactionManager) {
            final AlwaysRejectingThreadPoolTaskExecutor rejectingExecutor = new AlwaysRejectingThreadPoolTaskExecutor();
            rejectingExecutor.setCorePoolSize(5);
            rejectingExecutor.setMaxPoolSize(10);
            rejectingExecutor.setQueueCapacity(100);
            rejectingExecutor.setThreadNamePrefix("test-rejecting-executor-");
            rejectingExecutor.initialize();
            return new JobExecutor(jobService, taskRegistry, transactionManager, rejectingExecutor);
        }
    }

    static class AlwaysRejectingThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
        @Override
        public void execute(final Runnable task) {
            throw new TaskRejectedException(this,
                    task,
                    new RejectedExecutionException("Simulated pool saturation"));
        }
    }
}
