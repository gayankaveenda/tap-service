package au.com.transport.tapservice.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JobLock")
class JobLockTest {

    private JobLock jobLock;

    @BeforeEach
    void setUp() {
        jobLock = new JobLock();
    }

    @Test
    @DisplayName("tryLock() returns true when not locked")
    void tryLockSucceedsWhenFree() {
        assertThat(jobLock.tryLock()).isTrue();
    }

    @Test
    @DisplayName("tryLock() returns false when already locked")
    void tryLockFailsWhenAlreadyLocked() {
        jobLock.tryLock();                       // acquire first
        assertThat(jobLock.tryLock()).isFalse(); // second attempt fails
    }

    @Test
    @DisplayName("unlock() releases lock so next tryLock() succeeds")
    void unlockReleasesLock() {
        jobLock.tryLock();
        jobLock.unlock();
        assertThat(jobLock.tryLock()).isTrue();
    }

    @Test
    @DisplayName("unlock() on already unlocked lock does not throw")
    void unlockOnUnlockedIsIdempotent() {
        // No exception expected
        jobLock.unlock();
        assertThat(jobLock.tryLock()).isTrue();
    }

    @Test
    @DisplayName("lock/unlock/lock cycle works correctly")
    void lockUnlockCycle() {
        assertThat(jobLock.tryLock()).isTrue();   // lock
        jobLock.unlock();                          // unlock
        assertThat(jobLock.tryLock()).isTrue();   // lock again
        assertThat(jobLock.tryLock()).isFalse();  // second lock fails
        jobLock.unlock();                          // unlock again
        assertThat(jobLock.tryLock()).isTrue();   // lock once more
    }

    @Test
    @DisplayName("concurrent threads — only one acquires the lock")
    void onlyOneThreadAcquiresLock() throws InterruptedException {
        int threadCount = 10;
        AtomicInteger lockAcquired = new AtomicInteger(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // all threads start simultaneously
                    if (jobLock.tryLock()) {
                        lockAcquired.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // release all threads at once
        doneLatch.await();
        executor.shutdown();

        // Only exactly one thread should have acquired the lock
        assertThat(lockAcquired.get()).isEqualTo(1);
    }
}
