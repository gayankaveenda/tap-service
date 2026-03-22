package au.com.transport.tapservice.processor;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedProcessingJob {

    private final TapEventRepository tapEventRepository;
    private final TripOrchestrator tripOrchestrator;
    private final JobLock jobLock;

    //add page size from application.yml
    @Value("${app.processing.scheduler.page-size:4}")
    private int pageSize;

    @Value("${app.processing.scheduler.orphan-cutoff-minutes:3}")
    private int orphanCutoffMinutes;

    @Scheduled(cron = "0 0/2 * * * *") // every 30 mins
    public void processOrphans() {

        if (!jobLock.tryLock()) {
            log.debug("Skipping Orphan Cleanup job - another job is running");
            return;
        }

        try {
            //two minutes for our testing purposes, but in production this would be 2 hours or more depending on the expected delay for late tap offs
            LocalDateTime testCutoff = LocalDateTime.now().minusMinutes(orphanCutoffMinutes);

            List<TapEvent> orphans =
                    tapEventRepository.findOrphanCandidates(testCutoff, PageRequest.of(0, pageSize)); // > 2 hours

            if (orphans.isEmpty()) {
                log.debug("Orphan Cleanup job - No orphan candidates found");
                return;
            }

            TripOrchestrator.ProcessingResult processingResult = tripOrchestrator.processOrphanedTapOns(orphans);

            //log only if one of the counts is greater than 0 to reduce noise in logs
            if (processingResult.tripsCreated() > 0 || processingResult.unmatched() > 0 || processingResult.errors() > 0 || processingResult.previouslyProcessed() > 0) {
                log.info("Orphan Cleanup job complete: {}", processingResult);
            }

        } finally {
            jobLock.unlock();
        }
    }
}