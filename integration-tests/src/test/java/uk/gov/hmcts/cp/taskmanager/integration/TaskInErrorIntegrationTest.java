package uk.gov.hmcts.cp.taskmanager.integration;

import static jakarta.json.Json.createObjectBuilder;
import static java.time.ZonedDateTime.now;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.INPROGRESS;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.integration.service.TaskStatus;
import uk.gov.hmcts.cp.taskmanager.integration.service.TaskStatusService;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.repository.JobsRepository;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public class TaskInErrorIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobsRepository jobsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskStatusService taskStatusService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("delete from JOBS");
        final Integer jobs = jdbcTemplate.queryForObject("select count(*) from JOBS", Integer.class);
        assertThat(jobs).isEqualTo(0);
    }

    @Test
    void testErrorTaskWithNoRetryAttemptsShouldRunOnlyOnce() throws InterruptedException {
        // Given - Create a job with error task
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_ERROR_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                false
        );
        executionService.executeWith(executionInfo);

        assertTaskExecutedOnlyOnce(taskId, 1);
        Thread.sleep(4000);
        assertTaskExecutedOnlyOnce(taskId, 1);

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            // Job should still exist (not deleted) because it's status is INPROGRESS
            assertThat(jobs).isNotEmpty();

            Job job = jobs.get(0);
            // Retry attempts should be set to 0
            assertThat(job.getRetryAttemptsRemaining()).isEqualTo(0);
        });
    }

    @Test
    void testErrorRetryTaskWithRetryAttemptsShouldRunAllTheRetryAttempts() throws InterruptedException {
        // Given - Create a job with error retry task
        final UUID taskId = randomUUID();
        ExecutionInfo executionInfo = new ExecutionInfo(
                createObjectBuilder()
                        .add("test", "data")
                        .add(ID_KEY, taskId.toString())
                        .build(),
                "TEST_ERROR_RETRY_TASK",
                now().minusSeconds(1),
                ExecutionStatus.STARTED,
                true
        );
        executionService.executeWith(executionInfo);

        assertTaskExecutedOnlyOnce(taskId, 3);
        Thread.sleep(4000);
        assertTaskExecutedOnlyOnce(taskId, 3);

        // When - Wait for first execution
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Job> jobs = jobsRepository.findAll();
            // Job should still exist (not deleted) because it's status is INPROGRESS
            assertThat(jobs).isNotEmpty();

            Job job = jobs.get(0);
            // Retry attempts should be set to 0
            assertThat(job.getRetryAttemptsRemaining()).isEqualTo(0);
        });
    }

    private void assertTaskExecutedOnlyOnce(final UUID taskId, final int retryAttempts) {
        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            final Optional<TaskStatus> task = taskStatusService.getById(taskId);
            assertThat(task.isEmpty()).isFalse();
            assertThat(task.get().getStatus().equals(INPROGRESS.name())).isTrue();
            assertThat(task.get().getJobData().getInt(ATTEMPTS_KEY)).isEqualTo(retryAttempts);
        });
    }
}
