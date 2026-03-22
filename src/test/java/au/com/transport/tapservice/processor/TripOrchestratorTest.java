package au.com.transport.tapservice.processor;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.ingestion.TapType;
import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import au.com.transport.tapservice.repository.trip.TripRepository;
import au.com.transport.tapservice.service.trip.TripStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TripOrchestrator")
@ExtendWith(MockitoExtension.class)
class TripOrchestratorTest {

    @Mock
    private TapEventRepository tapEventRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripStateService tripStateService;

    @InjectMocks
    private TripOrchestrator orchestrator;

    private static final LocalDateTime ON_TIME = LocalDateTime.of(2023, 1, 22, 13, 0, 0);
    private static final LocalDateTime OFF_TIME = LocalDateTime.of(2023, 1, 22, 13, 5, 0);

    @BeforeEach
    void setUp() {
        // Set batchSize via reflection — @Value not injected in unit tests
        ReflectionTestUtils.setField(orchestrator, "batchSize", 100);
        // saveAll returns its input by default
        lenient().when(tapEventRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(tripRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private TapEvent tapOn(Long id, String stopId, String busId, String panHash) {
        TapEvent e = new TapEvent();
        e.setId(id);
        e.setTapType(TapType.ON);
        e.setStopId(stopId);
        e.setBusId(busId);
        e.setPanHash(panHash);
        e.setMaskedPan("****0001");
        e.setCompanyId("Company1");
        e.setTappedAt(ON_TIME);
        e.setStatus(TapEvent.TapEventStatus.PENDING);
        return e;
    }

    private TapEvent tapOff(Long id, String busId, String panHash) {
        return tapOff(id, "Stop2", busId, panHash, OFF_TIME);
    }

    private TapEvent tapOff(Long id, String stopId, String busId, String panHash, LocalDateTime time) {
        TapEvent e = new TapEvent();
        e.setId(id);
        e.setTapType(TapType.OFF);
        e.setStopId(stopId);
        e.setBusId(busId);
        e.setPanHash(panHash);
        e.setMaskedPan("****0001");
        e.setCompanyId("Company1");
        e.setTappedAt(time);
        e.setStatus(TapEvent.TapEventStatus.PENDING);
        return e;
    }

    private Trip completedTrip(Long tapOnId, Long tapOffId) {
        return Trip.builder()
                .started(ON_TIME).finished(OFF_TIME)
                .durationSecs(300L)
                .fromStopId("Stop1").toStopId("Stop2")
                .chargeAmount(new BigDecimal("3.25"))
                .companyId("Company1").busId("Bus37")
                .maskedPan("****0001").panHash("hash1")
                .status(TripStatus.COMPLETED)
                .tapOnEventId(tapOnId).tapOffEventId(tapOffId)
                .build();
    }

    private Trip negativeDurationTrip() {
        return Trip.builder()
                .started(OFF_TIME).finished(ON_TIME)   // reversed — negative duration
                .durationSecs(-300L)
                .fromStopId("Stop1").toStopId("Stop2")
                .chargeAmount(new BigDecimal("3.25"))
                .companyId("Company1").busId("Bus37")
                .maskedPan("****0001").panHash("hash1")
                .status(TripStatus.COMPLETED)
                .tapOnEventId(1L).tapOffEventId(2L)
                .build();
    }

    // -------------------------------------------------------
    // processBatch()
    // -------------------------------------------------------

    @Nested
    @DisplayName("processBatch()")
    class ProcessBatch {

        @Test
        @DisplayName("matched ON+OFF creates trip — tripsCreated=1")
        void matchedPairCreatesTrip() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on));
            when(tripStateService.resolve(on, off)).thenReturn(completedTrip(1L, 2L));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(false);
            when(tripRepository.saveAll(anyCollection()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            var result = orchestrator.processBatch(List.of(on, off));

            assertThat(result.tripsCreated()).isEqualTo(1);
            assertThat(result.unmatched()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0);
        }

        @Test
        @DisplayName("matched ON+OFF marks both as PROCESSED")
        void matchedPairMarkedProcessed() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on));
            when(tripStateService.resolve(on, off)).thenReturn(completedTrip(1L, 2L));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(false);

            orchestrator.processBatch(List.of(on, off));

            assertThat(on.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
            assertThat(off.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
        }

        @Test
        @DisplayName("TAP OFF with no matching TAP ON → UNMATCHED, unmatched=1")
        void unmatchedTapOff() {
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of());

            var result = orchestrator.processBatch(List.of(off));

            assertThat(result.unmatched()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(0);
            assertThat(off.getStatus()).isEqualTo(TapEvent.TapEventStatus.UNMATCHED);
        }

        @Test
        @DisplayName("TAP OFF before TAP ON — negative duration → INVALID, errors=1")
        void negativeDurationMarkedInvalid() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off = tapOff(2L, "Stop2", "Bus37", "hash1",
                    ON_TIME.minusMinutes(5));   // OFF is before ON

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on));
            when(tripStateService.resolve(on, off))
                    .thenReturn(negativeDurationTrip());

            var result = orchestrator.processBatch(List.of(on, off));

            assertThat(result.errors()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(0);
            assertThat(off.getStatus()).isEqualTo(TapEvent.TapEventStatus.INVALID);
        }

        @Test
        @DisplayName("duplicate ON — two ONs matched, first marked CANCELED_DUPLICATE")
        void duplicateOnFirstMarkedCanceledDuplicate() {
            TapEvent on1 = tapOn(1L, "Stop1", "Bus37", "hash1"); // older — will be duplicate
            TapEvent on2 = tapOn(2L, "Stop1", "Bus37", "hash1"); // newer — will be used
            TapEvent off = tapOff(3L, "Bus37", "hash1");

            // Returns both — newest first (ordered DESC)
            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on2, on1));   // on2 first = latest
            when(tripStateService.resolve(on2, off)).thenReturn(completedTrip(2L, 3L));
            when(tripRepository.existsByTapOnEventId(2L)).thenReturn(false);

            var result = orchestrator.processBatch(List.of(on1, on2, off));

            // Older ON is canceled
            assertThat(on1.getStatus()).isEqualTo(TapEvent.TapEventStatus.CANCELED_DUPLICATE);
            // Newer ON is used for the trip
            assertThat(on2.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
            assertThat(result.tripsCreated()).isEqualTo(1);
        }

        @Test
        @DisplayName("idempotency — trip already exists for tapOnId → previouslyProcessed=1, no new trip")
        void idempotentProcessing() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on));
            when(tripStateService.resolve(on, off)).thenReturn(completedTrip(1L, 2L));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(true); // already exists

            var result = orchestrator.processBatch(List.of(on, off));

            assertThat(result.previouslyProcessed()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(0);
            // No new trip saved
            verify(tripRepository, never()).saveAll(argThat(c ->
                    ((Collection<?>) c).stream()
                            .anyMatch(t -> t instanceof Trip)
            ));
        }

        @Test
        @DisplayName("exception during processing marks TAP OFF as FAILED — batch continues")
        void exceptionMarksTapOffFailed() {
            TapEvent on1 = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off1 = tapOff(2L, "Bus37", "hash1");
            TapEvent on2 = tapOn(3L, "Stop1", "Bus37", "hash2");
            TapEvent off2 = tapOff(4L, "Bus37", "hash2");

            // First pair throws
            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenThrow(new RuntimeException("DB error"));

            // Second pair succeeds
            when(tapEventRepository.findPendingTapOn("hash2", "Bus37"))
                    .thenReturn(List.of(on2));
            when(tripStateService.resolve(on2, off2)).thenReturn(completedTrip(3L, 4L));
            when(tripRepository.existsByTapOnEventId(3L)).thenReturn(false);

            var result = orchestrator.processBatch(List.of(on1, on2, off1, off2));

            // First pair errors, second succeeds
            assertThat(result.errors()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(1);
            assertThat(off1.getStatus()).isEqualTo(TapEvent.TapEventStatus.FAILED);
        }

        @Test
        @DisplayName("TAP ON events in batch are ignored by processBatch — only TAP OFFs drive matching")
        void tapOnsIgnoredByProcessBatch() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            // No TAP OFFs in batch

            var result = orchestrator.processBatch(List.of(on));

            assertThat(result.tripsCreated()).isEqualTo(0);
            assertThat(result.unmatched()).isEqualTo(0);
            verify(tapEventRepository, never()).findPendingTapOn(any(), any());
        }

        @Test
        @DisplayName("different buses — TAP ON Bus10, TAP OFF Bus99 — no match")
        void differentBusNoMatch() {
            TapEvent on = tapOn(1L, "Stop1", "Bus10", "hash1");
            TapEvent off = tapOff(2L, "Bus99", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus99"))
                    .thenReturn(List.of()); // Bus99 has no pending ON

            var result = orchestrator.processBatch(List.of(on, off));

            assertThat(result.unmatched()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(0);
        }

        @Test
        @DisplayName("result jobName is PENDING_TAPS")
        void resultJobName() {
            var result = orchestrator.processBatch(List.of());
            assertThat(result.jobName()).isEqualTo("PENDING_TAPS");
        }

        @Test
        @DisplayName("empty batch returns all zeros")
        void emptyBatchAllZeros() {
            var result = orchestrator.processBatch(List.of());

            assertThat(result.tripsCreated()).isZero();
            assertThat(result.unmatched()).isZero();
            assertThat(result.errors()).isZero();
            assertThat(result.previouslyProcessed()).isZero();
        }
    }

    // -------------------------------------------------------
    // processOrphanedTapOns()
    // -------------------------------------------------------

    @Nested
    @DisplayName("processOrphanedTapOns()")
    class ProcessOrphanedTapOns {

        @Test
        @DisplayName("PENDING TAP ON resolved as INCOMPLETE — tripsCreated=1")
        void pendingTapOnResolvedIncomplete() {
            TapEvent on = tapOn(1L, "Stop2", "Bus37", "hash1");

            Trip incomplete = Trip.builder()
                    .started(ON_TIME).finished(null).durationSecs(0L)
                    .fromStopId("Stop2").toStopId(null)
                    .chargeAmount(new BigDecimal("5.50"))
                    .companyId("Company1").busId("Bus37")
                    .maskedPan("****0001").panHash("hash1")
                    .status(TripStatus.INCOMPLETE)
                    .tapOnEventId(1L).tapOffEventId(null)
                    .build();

            when(tapEventRepository.findById(1L)).thenReturn(Optional.of(on));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(false);
            when(tripStateService.resolveIncomplete(on)).thenReturn(incomplete);

            var result = orchestrator.processOrphanedTapOns(List.of(on));

            assertThat(result.tripsCreated()).isEqualTo(1);
            assertThat(on.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
        }

        @Test
        @DisplayName("already PROCESSED TAP ON skipped — status not PENDING")
        void alreadyProcessedSkipped() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            on.setStatus(TapEvent.TapEventStatus.PROCESSED); // already done

            when(tapEventRepository.findById(1L)).thenReturn(Optional.of(on));

            var result = orchestrator.processOrphanedTapOns(List.of(on));

            assertThat(result.tripsCreated()).isEqualTo(0);
            verify(tripStateService, never()).resolveIncomplete(any());
        }

        @Test
        @DisplayName("trip already exists for TAP ON — idempotency — previouslyProcessed=1")
        void idempotentOrphanProcessing() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");

            when(tapEventRepository.findById(1L)).thenReturn(Optional.of(on));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(true);

            var result = orchestrator.processOrphanedTapOns(List.of(on));

            assertThat(result.previouslyProcessed()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(0);
            assertThat(on.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
        }

        @Test
        @DisplayName("TAP ON not found in DB — skipped gracefully, no exception")
        void tapOnNotFoundSkipped() {
            TapEvent on = tapOn(99L, "Stop1", "Bus37", "hash1");

            when(tapEventRepository.findById(99L)).thenReturn(Optional.empty());

            var result = orchestrator.processOrphanedTapOns(List.of(on));

            assertThat(result.tripsCreated()).isEqualTo(0);
            assertThat(result.errors()).isEqualTo(0); // not an error — just skipped
        }

        @Test
        @DisplayName("TAP OFF events in list are filtered out — only TAP ONs processed")
        void tapOffsFilteredOut() {
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            var result = orchestrator.processOrphanedTapOns(List.of(off));

            assertThat(result.tripsCreated()).isEqualTo(0);
            verify(tapEventRepository, never()).findById(any());
        }

        @Test
        @DisplayName("exception during resolution marks TAP ON as FAILED — batch continues")
        void exceptionMarksTapOnFailed() {
            TapEvent on1 = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent on2 = tapOn(2L, "Stop2", "Bus37", "hash2");

            Trip incomplete = Trip.builder()
                    .started(ON_TIME).durationSecs(0L)
                    .fromStopId("Stop2").chargeAmount(new BigDecimal("5.50"))
                    .companyId("Company1").busId("Bus37")
                    .maskedPan("****0001").panHash("hash2")
                    .status(TripStatus.INCOMPLETE).tapOnEventId(2L)
                    .build();

            when(tapEventRepository.findById(1L)).thenReturn(Optional.of(on1));
            when(tapEventRepository.findById(2L)).thenReturn(Optional.of(on2));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(false);
            when(tripRepository.existsByTapOnEventId(2L)).thenReturn(false);

            // First throws, second succeeds
            when(tripStateService.resolveIncomplete(on1))
                    .thenThrow(new RuntimeException("Fare not found"));
            when(tripStateService.resolveIncomplete(on2))
                    .thenReturn(incomplete);

            var result = orchestrator.processOrphanedTapOns(List.of(on1, on2));

            assertThat(result.errors()).isEqualTo(1);
            assertThat(result.tripsCreated()).isEqualTo(1);
            assertThat(on1.getStatus()).isEqualTo(TapEvent.TapEventStatus.FAILED);
            assertThat(on2.getStatus()).isEqualTo(TapEvent.TapEventStatus.PROCESSED);
        }

        @Test
        @DisplayName("result jobName is ORPHAN_CLEANUP")
        void resultJobName() {
            var result = orchestrator.processOrphanedTapOns(List.of());
            assertThat(result.jobName()).isEqualTo("ORPHAN_CLEANUP");
        }

        @Test
        @DisplayName("empty batch returns all zeros")
        void emptyBatch() {
            var result = orchestrator.processOrphanedTapOns(List.of());
            assertThat(result.tripsCreated()).isZero();
            assertThat(result.errors()).isZero();
        }
    }

    @Nested
    @DisplayName("batch flush behaviour")
    class BatchFlush {

        @Test
        @DisplayName("flush triggered when force=true even with small batch")
        void flushOnForce() {
            TapEvent on = tapOn(1L, "Stop1", "Bus37", "hash1");
            TapEvent off = tapOff(2L, "Bus37", "hash1");

            when(tapEventRepository.findPendingTapOn("hash1", "Bus37"))
                    .thenReturn(List.of(on));
            when(tripStateService.resolve(on, off)).thenReturn(completedTrip(1L, 2L));
            when(tripRepository.existsByTapOnEventId(1L)).thenReturn(false);

            orchestrator.processBatch(List.of(on, off));

            // saveAll must have been called at least once (final force flush)
            verify(tapEventRepository, atLeastOnce()).saveAll(any());
            verify(tripRepository, atLeastOnce()).saveAll(any());
        }

        @Test
        @DisplayName("no saveAll called when batch is empty")
        void noSaveAllWhenEmpty() {
            orchestrator.processBatch(List.of());

            verify(tapEventRepository, never()).saveAll(any());
            verify(tripRepository, never()).saveAll(any());
        }
    }
}
