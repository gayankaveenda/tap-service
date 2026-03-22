package au.com.transport.tapservice.integration;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.trip.TripStatus;
import au.com.transport.tapservice.processor.TripOrchestrator;
import au.com.transport.tapservice.repository.ingestion.FailedIngestionRepository;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import au.com.transport.tapservice.repository.trip.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Full Pipeline Integration Test")
class FullPipelineIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TapEventRepository tapEventRepository;
    @Autowired
    TripRepository tripRepository;
    @Autowired
    FailedIngestionRepository failedIngestionRepository;
    @Autowired
    TripOrchestrator tripOrchestrator;

    // FareRule seed — inserted directly so we don't need Flyway
    @Autowired
    au.com.transport.tapservice.repository.trip.FareRuleRepository fareRuleRepository;

    @BeforeEach
    void setUp() {
        // Clean slate
        tripRepository.deleteAll();
        tapEventRepository.deleteAll();
        failedIngestionRepository.deleteAll();
        fareRuleRepository.deleteAll();

        // Seed fare rules (assignment matrix — bidirectional)
        seedFareRules();
    }

    private void seedFareRules() {
        List<au.com.transport.tapservice.entity.trip.FareRule> rules = List.of(
                rule("Stop1", "Stop2", "3.25"),
                rule("Stop2", "Stop1", "3.25"),
                rule("Stop2", "Stop3", "5.50"),
                rule("Stop3", "Stop2", "5.50"),
                rule("Stop1", "Stop3", "7.30"),
                rule("Stop3", "Stop1", "7.30")
        );
        fareRuleRepository.saveAll(rules);
    }

    private au.com.transport.tapservice.entity.trip.FareRule rule(
            String from, String to, String amount) {
        var r = new au.com.transport.tapservice.entity.trip.FareRule();
        r.setFromStopId(from);
        r.setToStopId(to);
        r.setFareAmount(new java.math.BigDecimal(amount));
        return r;
    }

    private MockMultipartFile csvFile(String classpathResource) throws Exception {
        byte[] content = Files.readAllBytes(
                new ClassPathResource(classpathResource).getFile().toPath());
        return new MockMultipartFile(
                "file",                             // matches @RequestParam("file")
                classpathResource,
                "text/csv",
                content);
    }

    private void ingestAndProcess(String classpathCsv) throws Exception {
        mockMvc.perform(multipart("/api/v1/ingest")
                .file(csvFile(classpathCsv)));

        List<TapEvent> pending = tapEventRepository.findAllPending();
        tripOrchestrator.processBatch(pending);

        // Resolve any remaining PENDING TAP ONs as INCOMPLETE
        List<TapEvent> orphans = tapEventRepository.findAllPending().stream()
                .filter(e -> e.getTapType() == au.com.transport.tapservice.entity.ingestion.TapType.ON)
                .toList();
        tripOrchestrator.processOrphanedTapOns(orphans);
    }

    @Nested
    @DisplayName("Ingestion")
    class Ingestion {

        @Test
        @DisplayName("upload assignment CSV → 6 tap events saved as PENDING")
        void uploadSaves6TapEvents() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                            .file(csvFile("taps-assignment.csv")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSaved").value(6));

            assertThat(tapEventRepository.count()).isEqualTo(6);
            assertThat(tapEventRepository.findAllPending()).hasSize(6);
        }

        @Test
        @DisplayName("upload returns 200 when all rows valid")
        void returns200WhenAllValid() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                            .file(csvFile("taps-assignment.csv")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("upload returns 207 when some rows invalid")
        void returns207WhenPartialFailure() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                            .file(csvFile("taps-invalid-rows.csv")))
                    .andExpect(status().is(207));
        }

        @Test
        @DisplayName("invalid rows saved to failed_ingestion_records — not lost")
        void invalidRowsSavedToFailedIngestion() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                    .file(csvFile("taps-invalid-rows.csv")));

            // 3 invalid rows: BAD ROW, INVALID_DATE, INVALIDPAN
            assertThat(failedIngestionRepository.count()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("idempotency — uploading same file twice does not create duplicates")
        void idempotentUpload() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                    .file(csvFile("taps-assignment.csv")));
            mockMvc.perform(multipart("/api/v1/ingest")
                    .file(csvFile("taps-assignment.csv")));

            // Still 6 — second upload skipped all rows
            assertThat(tapEventRepository.count()).isEqualTo(6);
        }

        @Test
        @DisplayName("raw PAN never stored — panHash and maskedPan present instead")
        void rawPanNeverStored() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                    .file(csvFile("taps-assignment.csv")));

            tapEventRepository.findAll().forEach(event -> {
                assertThat(event.getPanHash()).isNotNull().hasSize(64);
                assertThat(event.getMaskedPan()).startsWith("****");
                // No raw PAN field on TapEvent — confirmed by entity design
            });
        }
    }

    @Nested
    @DisplayName("Trip Processing")
    class TripProcessing {

        @Test
        @DisplayName("assignment sample CSV produces exactly 3 trips")
        void produces3Trips() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            assertThat(tripRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("row 1+2: COMPLETED Stop1→Stop2 $3.25 duration 300s")
        void completedTrip() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            var completed = tripRepository.findByStatusOrderByStartedAsc(TripStatus.COMPLETED);
            assertThat(completed).hasSize(1);

            var trip = completed.get(0);
            assertThat(trip.getFromStopId()).isEqualTo("Stop1");
            assertThat(trip.getToStopId()).isEqualTo("Stop2");
            assertThat(trip.getChargeAmount()).isEqualByComparingTo("3.25");
            assertThat(trip.getMaskedPan()).isEqualTo("****5559");
            assertThat(trip.getFinished()).isNotNull();
        }

        @Test
        @DisplayName("row 3: INCOMPLETE Stop3 $7.30 no finished no toStopId")
        void incompleteTrip() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            var incomplete = tripRepository.findByStatusOrderByStartedAsc(TripStatus.INCOMPLETE);
            assertThat(incomplete).hasSize(1);

            var trip = incomplete.get(0);
            assertThat(trip.getFromStopId()).isEqualTo("Stop3");
            assertThat(trip.getToStopId()).isNull();
            assertThat(trip.getChargeAmount()).isEqualByComparingTo("7.30");
            assertThat(trip.getDurationSecs()).isZero();
            assertThat(trip.getFinished()).isNull();
            assertThat(trip.getMaskedPan()).isEqualTo("****1111");
        }

        @Test
        @DisplayName("row 4+5: CANCELLED Stop1→Stop1 $0.00")
        void cancelledTrip() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            var cancelled = tripRepository.findByStatusOrderByStartedAsc(TripStatus.CANCELLED);
            assertThat(cancelled).hasSize(1);

            var trip = cancelled.get(0);
            assertThat(trip.getFromStopId()).isEqualTo("Stop1");
            assertThat(trip.getToStopId()).isEqualTo("Stop1");
            assertThat(trip.getChargeAmount()).isEqualByComparingTo("0.00");
            assertThat(trip.getMaskedPan()).isEqualTo("****1111");
        }

        @Test
        @DisplayName("Test All Status: One of each status")
        void unmatchedTapOffCreatesNoTrip() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            // We get 3 trips one of complete, cancelled and incomplete
            assertThat(tripRepository.count()).isEqualTo(3);

            // no pending rows
            List<TapEvent> unmatched = tapEventRepository.findAll().stream()
                    .filter(e -> e.getStatus() == TapEvent.TapEventStatus.PENDING)
                    .toList();
            assertThat(unmatched).hasSize(0);
        }

        @Test
        @DisplayName("processing is idempotent — running twice produces same 3 trips")
        void processingIdempotent() throws Exception {
            mockMvc.perform(multipart("/api/v1/ingest")
                    .file(csvFile("taps-assignment.csv")));

            // Process twice
            List<TapEvent> pending1 = tapEventRepository.findAllPending();
            tripOrchestrator.processBatch(pending1);
            tripOrchestrator.processOrphanedTapOns(
                    tapEventRepository.findAllPending().stream()
                            .filter(e -> e.getTapType() ==
                                    au.com.transport.tapservice.entity.ingestion.TapType.ON)
                            .toList());

            List<TapEvent> pending2 = tapEventRepository.findAllPending();
            tripOrchestrator.processBatch(pending2);
            tripOrchestrator.processOrphanedTapOns(pending2);

            // Still exactly 3 trips
            assertThat(tripRepository.count()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Export CSV API")
    class ExportApi {

        @Test
        @DisplayName("GET /trips/export returns text/csv content type")
        void exportReturnsCsvContentType() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            mockMvc.perform(get("/api/v1/trips/export"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/csv"));
        }

        @Test
        @DisplayName("export has Content-Disposition attachment header with trips.csv")
        void exportHasContentDispositionHeader() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            mockMvc.perform(get("/api/v1/trips/export"))
                    .andExpect(header().string("Content-Disposition",
                            containsString("trips.csv")));
        }

        @Test
        @DisplayName("export CSV contains correct header columns")
        void exportContainsHeaders() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            mockMvc.perform(get("/api/v1/trips/export"))
                    .andExpect(content().string(containsString("Started")))
                    .andExpect(content().string(containsString("Finished")))
                    .andExpect(content().string(containsString("DurationSecs")))
                    .andExpect(content().string(containsString("ChargeAmount")))
                    .andExpect(content().string(containsString("Status")));
        }

        @Test
        @DisplayName("export contains all 3 expected trip rows")
        void exportContains3Rows() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            String csv = mockMvc.perform(get("/api/v1/trips/export"))
                    .andReturn().getResponse().getContentAsString();

            long dataRows = csv.lines()
                    .filter(l -> !l.isBlank())
                    .count() - 1; // subtract header

            assertThat(dataRows).isEqualTo(3);
        }

        @Test
        @DisplayName("export contains correct fare amounts")
        void exportContainsCorrectFares() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            mockMvc.perform(get("/api/v1/trips/export"))
                    .andExpect(content().string(containsString("$3.25")))
                    .andExpect(content().string(containsString("$7.30")))
                    .andExpect(content().string(containsString("$0.00")));
        }

        @Test
        @DisplayName("export PAN is masked — raw PAN never appears")
        void exportPanIsMasked() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            String csv = mockMvc.perform(get("/api/v1/trips/export"))
                    .andReturn().getResponse().getContentAsString();

            // Masked PANs present
            assertThat(csv).contains("****5559");
            assertThat(csv).contains("****1111");

            // Raw PANs never present
            assertThat(csv).doesNotContain("5500005555555559");
            assertThat(csv).doesNotContain("4111111111111111");
        }

        @Test
        @DisplayName("export INCOMPLETE row has empty Finished and ToStopId — no 'null' string")
        void exportIncompleteRowNoNullString() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            String csv = mockMvc.perform(get("/api/v1/trips/export"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(csv).doesNotContain("null");
        }

        @Test
        @DisplayName("export with status filter returns only matching trips")
        void exportWithStatusFilter() throws Exception {
            ingestAndProcess("taps-assignment.csv");

            String csv = mockMvc.perform(
                            get("/api/v1/trips/export")
                                    .param("status", "COMPLETED"))
                    .andReturn().getResponse().getContentAsString();

            long dataRows = csv.lines().filter(l -> !l.isBlank()).count() - 1;
            assertThat(dataRows).isEqualTo(1);
            assertThat(csv).contains("COMPLETED");
            assertThat(csv).doesNotContain("INCOMPLETE");
            assertThat(csv).doesNotContain("CANCELLED");
        }

        @Test
        @DisplayName("empty database returns header row only")
        void exportEmptyDatabaseHeaderOnly() throws Exception {
            // No ingestion — trips table is empty

            String csv = mockMvc.perform(get("/api/v1/trips/export"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(1); // header only
        }
    }
}
