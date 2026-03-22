package au.com.transport.tapservice.processor;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JobLock {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean tryLock() {
        return running.compareAndSet(false, true);
    }

    public void unlock() {
        running.set(false);
    }

}
