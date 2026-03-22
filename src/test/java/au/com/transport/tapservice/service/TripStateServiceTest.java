package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.ingestion.TapType;
import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import au.com.transport.tapservice.service.trip.FareCalculatorService;
import au.com.transport.tapservice.service.trip.TripStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("TripStateService")
@ExtendWith(MockitoExtension.class)
class TripStateServiceTest {

    @Mock
    private FareCalculatorService fareCalculatorService;

    private TripStateService tripStateService;

    private static final LocalDateTime ON_TIME  = LocalDateTime.of(2023, 1, 22, 13, 0, 0);
    private static final LocalDateTime OFF_TIME = LocalDateTime.of(2023, 1, 22, 13, 5, 0);

    @BeforeEach
    void setUp() {
        tripStateService = new TripStateService(fareCalculatorService);
    }

    private TapEvent tapOn(String stopId) {
        return tapOn(stopId, "Bus37", ON_TIME);
    }

    private TapEvent tapOn(String stopId, String busId, LocalDateTime time) {
        TapEvent e = new TapEvent();
        e.setId(1L);
        e.setTapType(TapType.ON);
        e.setStopId(stopId);
        e.setBusId(busId);
        e.setCompanyId("Company1");
        e.setMaskedPan("****5559");
        e.setPanHash("abc123hash");
        e.setTappedAt(time);
        return e;
    }

    private TapEvent tapOff(String stopId) {
        return tapOff(stopId, "Bus37", OFF_TIME);
    }

    private TapEvent tapOff(String stopId, String busId, LocalDateTime time) {
        TapEvent e = new TapEvent();
        e.setId(2L);
        e.setTapType(TapType.OFF);
        e.setStopId(stopId);
        e.setBusId(busId);
        e.setCompanyId("Company1");
        e.setMaskedPan("****5559");
        e.setPanHash("abc123hash");
        e.setTappedAt(time);
        return e;
    }

    @Nested
    @DisplayName("COMPLETED trips")
    class CompletedTrips {

        @Test
        @DisplayName("different stops → COMPLETED status")
        void differentStopsCompleted() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
        }

        @Test
        @DisplayName("Stop1 → Stop2 charged $3.25")
        void stop1ToStop2Fare() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("3.25");
        }

        @Test
        @DisplayName("Stop2 → Stop3 charged $5.50")
        void stop2ToStop3Fare() {
            when(fareCalculatorService.getFare("Stop2", "Stop3"))
                .thenReturn(new BigDecimal("5.50"));

            Trip trip = tripStateService.resolve(tapOn("Stop2"), tapOff("Stop3"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("5.50");
        }

        @Test
        @DisplayName("Stop1 → Stop3 charged $7.30")
        void stop1ToStop3Fare() {
            when(fareCalculatorService.getFare("Stop1", "Stop3"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop3"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("duration calculated correctly — 5 minutes = 300 seconds")
        void durationCalculated() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getDurationSecs()).isEqualTo(300L);
        }

        @Test
        @DisplayName("started set from TAP ON tappedAt")
        void startedFromTapOn() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getStarted()).isEqualTo(ON_TIME);
        }

        @Test
        @DisplayName("finished set from TAP OFF tappedAt")
        void finishedFromTapOff() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getFinished()).isEqualTo(OFF_TIME);
        }

        @Test
        @DisplayName("fromStopId set from TAP ON stop")
        void fromStopFromTapOn() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getFromStopId()).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("toStopId set from TAP OFF stop")
        void toStopFromTapOff() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getToStopId()).isEqualTo("Stop2");
        }

        @Test
        @DisplayName("maskedPan carried from TAP ON")
        void maskedPanFromTapOn() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop2"));

            assertThat(trip.getMaskedPan()).isEqualTo("****5559");
        }

        @Test
        @DisplayName("tapOnEventId and tapOffEventId set correctly")
        void eventIdsSet() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            TapEvent on  = tapOn("Stop1");
            TapEvent off = tapOff("Stop2");
            Trip trip = tripStateService.resolve(on, off);

            assertThat(trip.getTapOnEventId()).isEqualTo(on.getId());
            assertThat(trip.getTapOffEventId()).isEqualTo(off.getId());
        }

        @Test
        @DisplayName("negative duration — TAP OFF before TAP ON — returns negative durationSecs")
        void negativeDurationWhenOffBeforeOn() {
            when(fareCalculatorService.getFare("Stop1", "Stop2"))
                .thenReturn(new BigDecimal("3.25"));

            // OFF time is BEFORE ON time — out of order events
            TapEvent on  = tapOn("Stop1", "Bus37", OFF_TIME);   // later time
            TapEvent off = tapOff("Stop2", "Bus37", ON_TIME);   // earlier time

            Trip trip = tripStateService.resolve(on, off);

            // Orchestrator checks durationSecs < 0 and marks INVALID
            assertThat(trip.getDurationSecs()).isNegative();
        }
    }

    // -------------------------------------------------------
    // CANCELLED trips
    // -------------------------------------------------------

    @Nested
    @DisplayName("CANCELLED trips")
    class CancelledTrips {

        @Test
        @DisplayName("same stop → CANCELLED status")
        void sameStopCancelled() {
            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop1"));

            assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        }

        @Test
        @DisplayName("CANCELLED trip charged $0.00")
        void cancelledZeroCharge() {
            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop1"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("fare calculator never called for CANCELLED trip")
        void fareNotCalledForCancelled() {
            tripStateService.resolve(tapOn("Stop1"), tapOff("Stop1"));

            verifyNoInteractions(fareCalculatorService);
        }

        @Test
        @DisplayName("CANCELLED trip has both fromStopId and toStopId set to same value")
        void cancelledBothStopsSet() {
            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop1"));

            assertThat(trip.getFromStopId()).isEqualTo("Stop1");
            assertThat(trip.getToStopId()).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("CANCELLED trip has duration calculated — passenger was on bus briefly")
        void cancelledDurationCalculated() {
            Trip trip = tripStateService.resolve(tapOn("Stop1"), tapOff("Stop1"));

            // Duration = OFF - ON = 5 minutes = 300 seconds
            assertThat(trip.getDurationSecs()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("INCOMPLETE trips")
    class IncompleteTrips {

        @Test
        @DisplayName("no TAP OFF → INCOMPLETE status")
        void noTapOffIncomplete() {
            when(fareCalculatorService.getMaxFare("Stop2"))
                .thenReturn(new BigDecimal("5.50"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop2"));

            assertThat(trip.getStatus()).isEqualTo(TripStatus.INCOMPLETE);
        }

        @Test
        @DisplayName("INCOMPLETE from Stop1 charged max fare $7.30")
        void incompleteFromStop1MaxFare() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("INCOMPLETE from Stop2 charged max fare $5.50")
        void incompleteFromStop2MaxFare() {
            when(fareCalculatorService.getMaxFare("Stop2"))
                .thenReturn(new BigDecimal("5.50"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop2"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("5.50");
        }

        @Test
        @DisplayName("INCOMPLETE from Stop3 charged max fare $7.30")
        void incompleteFromStop3MaxFare() {
            when(fareCalculatorService.getMaxFare("Stop3"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop3"));

            assertThat(trip.getChargeAmount()).isEqualByComparingTo("7.30");
        }

        @Test
        @DisplayName("INCOMPLETE trip has null finished")
        void incompleteNullFinished() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getFinished()).isNull();
        }

        @Test
        @DisplayName("INCOMPLETE trip has null toStopId")
        void incompleteNullToStop() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getToStopId()).isNull();
        }

        @Test
        @DisplayName("INCOMPLETE trip has duration 0")
        void incompleteZeroDuration() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getDurationSecs()).isZero();
        }

        @Test
        @DisplayName("INCOMPLETE trip has null tapOffEventId")
        void incompleteNullTapOffId() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getTapOffEventId()).isNull();
        }

        @Test
        @DisplayName("INCOMPLETE trip carries maskedPan from TAP ON")
        void incompleteMaskedPanFromTapOn() {
            when(fareCalculatorService.getMaxFare("Stop1"))
                .thenReturn(new BigDecimal("7.30"));

            Trip trip = tripStateService.resolveIncomplete(tapOn("Stop1"));

            assertThat(trip.getMaskedPan()).isEqualTo("****5559");
        }
    }
}
