package au.com.transport.tapservice.service.trip;

import au.com.transport.tapservice.CommonUtils;
import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Pure state transition logic.
 * Takes a TAP ON + optional TAP OFF, returns a fully resolved Trip.
 * No DB access — all persistence handled by TripOrchestrator.
 * <p>
 * Transitions:
 * ON + OFF same stop      → CANCELLED  ($0.00)
 * ON + OFF different stop → COMPLETED  (route fare)
 * ON + no OFF             → INCOMPLETE (max fare from origin)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripStateService {

    private final FareCalculatorService fareCalculatorService;

    /**
     * Resolve a COMPLETED or CANCELLED trip.
     * Called when a TAP OFF is matched to a TAP ON.
     */
    public Trip resolve(TapEvent tapOn, TapEvent tapOff) {

        boolean sameStop = tapOn.getStopId().equals(tapOff.getStopId());
        TripStatus status = sameStop ? TripStatus.CANCELLED : TripStatus.COMPLETED;

        BigDecimal charge = sameStop
                ? BigDecimal.ZERO
                : fareCalculatorService.getFare(tapOn.getStopId().trim(), tapOff.getStopId().trim());

        long durationSecs = Duration.between(tapOn.getTappedAt(), tapOff.getTappedAt()).getSeconds();

        log.debug("Trip resolved: status={}, from={}, to={}, charge={}, pan={}",
                status, tapOn.getStopId(), tapOff.getStopId(),
                formatCharge(charge), tapOn.getMaskedPan());

        return Trip.builder()
                .started(tapOn.getTappedAt())
                .finished(tapOff.getTappedAt())
                .durationSecs(durationSecs)
                .fromStopId(CommonUtils.normalize(tapOn.getStopId()))
                .toStopId(CommonUtils.normalize(tapOff.getStopId()))
                .chargeAmount(charge)
                .companyId(CommonUtils.normalize(tapOn.getCompanyId()))
                .busId(CommonUtils.normalize(tapOn.getBusId()))
                .maskedPan(CommonUtils.normalize(tapOn.getMaskedPan()))
                .panHash(CommonUtils.normalize(tapOn.getPanHash()))
                .status(status)
                .tapOnEventId(tapOn.getId())
                .tapOffEventId(tapOff.getId())
                .build();
    }

    /**
     * Resolve an INCOMPLETE trip.
     * Called when a TAP ON has no matching TAP OFF (TTL expired or end of file).
     */
    public Trip resolveIncomplete(TapEvent tapOn) {
        BigDecimal maxFare = fareCalculatorService.getMaxFare(tapOn.getStopId().trim());

        log.debug("Trip INCOMPLETE: from={}, maxFare={}, pan={}",
                tapOn.getStopId(), formatCharge(maxFare), tapOn.getMaskedPan());

        return Trip.builder()
                .started(tapOn.getTappedAt())
                .finished(null)
                .durationSecs(0L)
                .fromStopId(tapOn.getStopId())
                .toStopId(null)
                .chargeAmount(maxFare)
                .companyId(tapOn.getCompanyId())
                .busId(tapOn.getBusId())
                .maskedPan(tapOn.getMaskedPan())
                .status(TripStatus.INCOMPLETE)
                .tapOnEventId(tapOn.getId())
                .tapOffEventId(null)
                .panHash(tapOn.getPanHash())
                .build();
    }

    private String formatCharge(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
