package uk.gov.hmcts.cp.taskmanager.integration;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.json.Json;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTestConfiguration
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class JobConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private JobService jobService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void testReplicateRaceConditionWithPostgres() throws InterruptedException {
        // 1. Setup: Insert a single job into the real Postgres container
        final UUID workerId = randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            final Job job = new Job();
            job.setJobId(randomUUID());
            job.setAssignedTaskName("test-task");
            job.setJobData(Json.createObjectBuilder()
                    .add("testKey", "testValue")
                    .add("testNumber", 42)
                    .build());
            job.setAssignedTaskStartTime(ZonedDateTime.now().minusMinutes(1));
            job.setPriority(1);
            job.setRetryAttemptsRemaining(3);
            jobService.insertJob(job);
        });

        final int threadCount = 2;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        final CyclicBarrier barrier = new CyclicBarrier(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successfulClaims = new AtomicInteger();

        final Runnable task = () -> {
            try {
                barrier.await(); // force true race
                var jobs = jobService.assignJobsToWorkerBatch(workerId, 10);
                if (!jobs.isEmpty()) {
                    successfulClaims.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(e); // don't swallow exceptions
            } finally {
                latch.countDown();
            }
        };

        for (int i = 0; i < threadCount; i++) {
            executor.submit(task);
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Threads did not finish in time");
        executor.shutdownNow();

        assertEquals(1, successfulClaims.get(),
                "RACE CONDITION DETECTED: Multiple threads claimed the same job");
    }
}
