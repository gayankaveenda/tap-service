package au.com.transport.tapservice.processor;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.ingestion.TapType;
import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import au.com.transport.tapservice.repository.trip.TripRepository;
import au.com.transport.tapservice.service.trip.TripStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripOrchestrator {

    private final TapEventRepository tapEventRepository;
    private final TripRepository tripRepository;
    private final TripStateService tripStateService;

    @Value("${app.ingestion.batch-size}")
    private int batchSize;

    public ProcessingResult processBatch(List<TapEvent> pendingEvents) {
        int tripsCreated = 0;
        int unmatched = 0;
        int errors = 0;
        int previouslyProcessed = 0;

        List<TapEvent> tapOffs = pendingEvents.stream()
                .filter(e -> e.getTapType() == TapType.OFF)
                .toList();

        //current tapOn
        TapEvent tapOn = null;

        //successful trip list
        List<Trip> tripList = new ArrayList<>();

        //failed events to be marked as FAILED at the time of processing
        List<TapEvent> tapEventList = new ArrayList<>();

        // Step 1 — process TAP OFFs, try to match to existing PENDING TAP ONs
        for (TapEvent tapOff : tapOffs) {

            try {
                List<TapEvent> matchedOn = tapEventRepository
                        .findPendingTapOn(tapOff.getPanHash(), tapOff.getBusId());

                if (matchedOn.isEmpty()) {
                    log.debug("Unmatched TAP OFF: id={}, stop={}, pan={}",
                            tapOff.getId(), tapOff.getStopId(), tapOff.getMaskedPan());
                    tapOff.setStatus(TapEvent.TapEventStatus.UNMATCHED);
                    tapEventList.add(tapOff);
//                    tapEventRepository.save(tapOff);
                    unmatched++;
                    continue;
                }

                //get the latest tapOn because we ordered by tappedAt desc, so the first one is the latest tapOn
                tapOn = matchedOn.removeFirst();

                //clean up the rest of the matched taps if there are more than 1
                if (!matchedOn.isEmpty()) {
                    matchedOn.forEach(tap -> tap.setStatus(TapEvent.TapEventStatus.CANCELED_DUPLICATE));
                    tapEventList.addAll(matchedOn);
//                    tapEventRepository.saveAll(matchedOn);
                }

                Trip trip = tripStateService.resolve(tapOn, tapOff);

                //check if trip has negative duration, which means tapOff is before tapOn, which is invalid data
                if (trip.getDurationSecs() < 0) {
                    log.debug("Invalid trip with negative duration: tapOnId={}, tapOffId={}, pan={}, busId={}",
                            tapOn.getId(), tapOff.getId(), tapOn.getMaskedPan(), tapOn.getBusId());
                    tapOff.setStatus(TapEvent.TapEventStatus.INVALID);
                    tapEventList.add(tapOff);
//                    tapEventRepository.save(tapOff);
                    errors++;
                    continue;
                }

                tapOn.setStatus(TapEvent.TapEventStatus.PROCESSED);
                tapOff.setStatus(TapEvent.TapEventStatus.PROCESSED);
//                tapEventRepository.save(tapOn);
//                tapEventRepository.save(tapOff);

                tapEventList.add(tapOff);
                tapEventList.add(tapOn);

                // Idempotency — skip if trip already created for this tap ON
                if (tripRepository.existsByTapOnEventId(tapOn.getId())) {
                    log.debug("Duplicate — trip already exists for tapOnId={}", tapOn.getId());
                    previouslyProcessed++;
                    continue;
                }

                //otherwise, save the trip
//                tripRepository.save(trip);
                tripList.add(trip);
                tripsCreated++;
            } catch (Exception e) {
                log.error("Failed to process TAP OFF id={}: {}", tapOff.getId(), e.getMessage(), e);
                tapOff.setStatus(TapEvent.TapEventStatus.FAILED);

                if (tapOn != null) {
                    tapOn.setStatus(TapEvent.TapEventStatus.FAILED);
                }
                tapEventList.add(tapOn);
                errors++;
            }

            //save all trips and tap events in batch to optimize DB calls
            flushTripEventBatch(tripList,false);
            flushTapEventBatch(tapEventList,false);
        }

        //save all remaining trips and tap events in batch to optimize DB calls
        flushTripEventBatch(tripList, true);
        flushTapEventBatch(tapEventList, true);
        return new ProcessingResult("PENDING_TAPS", tripsCreated, unmatched, previouslyProcessed, errors);
    }

    public ProcessingResult processOrphanedTapOns(List<TapEvent> pendingEvents) {
        int tripsCreated = 0;
        int unmatched = 0;
        int errors = 0;
        int previouslyProcessed = 0;

        List<TapEvent> tapOns = pendingEvents.stream()
                .filter(e -> e.getTapType() == TapType.ON)
                .toList();

        //failed events to be marked as FAILED at the time of processing
        List<TapEvent> tapEventList = new ArrayList<>();

        //successful trip list
        List<Trip> tripList = new ArrayList<>();

        // Step 2 — process TAP ONs that are still PENDING (no TAP OFF matched them yet)
        // Reload to get updated statuses after step 1

        for (TapEvent orphanTapOn : tapOns) {

            Optional<TapEvent> optional = tapEventRepository.findById(orphanTapOn.getId());

            if (optional.isEmpty()) {
                log.warn("TapEvent not found for id={}", orphanTapOn.getId());
                continue; // skip, don’t kill batch
            }

            // Reload from DB to see if it was matched in step 1
            TapEvent current = optional.get();

            try {
                if (current.getStatus() != TapEvent.TapEventStatus.PENDING) {
                    continue; // already matched above
                }

                // Still PENDING — leave it for next run unless it's being force-resolved

                // Idempotency check (IMPORTANT)
                if (tripRepository.existsByTapOnEventId(current.getId())) {
                    current.setStatus(TapEvent.TapEventStatus.PROCESSED);
//                    tapEventRepository.save(current);
                    tapEventList.add(current);
                    previouslyProcessed++;
                    continue;
                }

                //truly orphaned tapOn, resolve as INCOMPLETE
                Trip incomplete = tripStateService.resolveIncomplete(current);

                //check if trip has negative duration, which means tapOff is before tapOn, which is invalid data
                if (incomplete.getDurationSecs() < 0) {
                    current.setStatus(TapEvent.TapEventStatus.INVALID);
                    tapEventList.add(current);
                    errors++;
                    continue;
                }

                //save the trip and mark the tapOn as PROCESSED
//                tripRepository.save(incomplete);
                tripList.add(incomplete);
                tripsCreated++;

                current.setStatus(TapEvent.TapEventStatus.PROCESSED);
//                tapEventRepository.save(current);
                tapEventList.add(current);
            } catch (Exception e) {
                log.error("Failed to process PENDING tapOn id={}: {}", current.getId(), e.getMessage(), e);
                current.setStatus(TapEvent.TapEventStatus.FAILED);
                tapEventList.add(current);
                errors++;
            }

            //save all trips and tap events in batch to optimize DB calls
            flushTripEventBatch(tripList, true);
            flushTapEventBatch(tapEventList, true);
        }
        return new ProcessingResult("ORPHAN_CLEANUP", tripsCreated, unmatched, previouslyProcessed, errors);
    }

    public record ProcessingResult(String jobName, int tripsCreated, int unmatched, int previouslyProcessed, int errors) {
    }

    protected void flushTapEventBatch(List<TapEvent> batch, boolean force) {
        if(!batch.isEmpty() && ((batch.size() >= batchSize) || force)) {
            tapEventRepository.saveAll(batch);
            batch.clear();
        }
    }

    protected void flushTripEventBatch(List<Trip> batch, boolean force) {
        if(!batch.isEmpty() && ((batch.size() >= batchSize) || force)) {
            tripRepository.saveAll(batch);
            batch.clear();
        }
    }
}
