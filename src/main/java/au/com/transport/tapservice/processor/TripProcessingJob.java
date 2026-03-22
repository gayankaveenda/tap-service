package au.com.transport.tapservice.processor;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripProcessingJob {

    private final TapEventRepository tapEventRepository;
    private final TripOrchestrator tripOrchestrator;
    private final JobLock jobLock;

    //add page size from application.yml
    @Value("${app.processing.scheduler.page-size:4}")
    private int pageSize;

    @Scheduled(fixedDelayString = "${app.processing.scheduler.fixed-delay-ms:5000}")
    public void processPendingTaps() {

        if (!jobLock.tryLock()) {
            log.debug("Skipping Match Pending job - another job is running");
            return;
        }

        try {
            List<TapEvent> batch = tapEventRepository.findPendingBatch(PageRequest.of(0, pageSize));

            if (batch.isEmpty()) {
                return;
            }

            log.info("Match Pending job processing {} events", batch.size());

            TripOrchestrator.ProcessingResult processingResult = tripOrchestrator.processBatch(batch);

            log.info("Match Pending job complete: {}", processingResult);

        } finally {
            jobLock.unlock();
        }
    }
}
