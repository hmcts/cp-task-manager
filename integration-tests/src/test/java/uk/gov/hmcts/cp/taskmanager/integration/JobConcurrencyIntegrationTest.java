package uk.gov.hmcts.cp.taskmanager.integration;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.hmcts.cp.taskmanager.integration.config.IntegrationTestConfiguration;
import uk.gov.hmcts.cp.taskmanager.persistence.entity.Job;
import uk.gov.hmcts.cp.taskmanager.persistence.service.JobService;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
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
        final UUID jobId = randomUUID();
        transactionTemplate.executeWithoutResult(status -> {
            final Job job = new Job();
            job.setJobId(jobId);
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
        final ExecutorService executor = newFixedThreadPool(threadCount);
        final CyclicBarrier barrier = new CyclicBarrier(threadCount);
        final AtomicInteger successfulClaims = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(threadCount);

        // 2. Run two threads simultaneously
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    barrier.await(); // Sync threads to hit the DB at once

                    final var jobs = jobService.assignJobsToWorkerBatch(jobId, 10);
                    if (!jobs.isEmpty()) {
                        successfulClaims.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Thread failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        // 3. Verification
        assertEquals(1, successfulClaims.get(),
                "RACE CONDITION DETECTED: Multiple threads claimed the same job!");
    }
}
