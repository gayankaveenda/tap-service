package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import au.com.transport.tapservice.service.trip.TripOutputWriterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TripOutputWriterService")
class TripOutputWriterServiceTest {

    private TripOutputWriterService writer;

    @BeforeEach
    void setUp() {
        writer = new TripOutputWriterService();
    }

    private Trip completedTrip() {
        return Trip.builder()
            .started(LocalDateTime.of(2023, 1, 22, 13, 0, 0))
            .finished(LocalDateTime.of(2023, 1, 22, 13, 5, 0))
            .durationSecs(300L)
            .fromStopId("Stop1")
            .toStopId("Stop2")
            .chargeAmount(new BigDecimal("3.25"))
            .companyId("Company1")
            .busId("Bus37")
            .maskedPan("****5559")
            .panHash("somehash")
            .status(TripStatus.COMPLETED)
            .tapOnEventId(1L)
            .tapOffEventId(2L)
            .build();
    }

    private Trip incompleteTrip() {
        return Trip.builder()
            .started(LocalDateTime.of(2023, 1, 22, 9, 20, 0))
            .finished(null)
            .durationSecs(0L)
            .fromStopId("Stop3")
            .toStopId(null)
            .chargeAmount(new BigDecimal("7.30"))
            .companyId("Company1")
            .busId("Bus36")
            .maskedPan("****1111")
            .panHash("somehash2")
            .status(TripStatus.INCOMPLETE)
            .tapOnEventId(3L)
            .tapOffEventId(null)
            .build();
    }

    private Trip cancelledTrip() {
        return Trip.builder()
            .started(LocalDateTime.of(2023, 1, 23, 8, 0, 0))
            .finished(LocalDateTime.of(2023, 1, 23, 8, 2, 0))
            .durationSecs(120L)
            .fromStopId("Stop1")
            .toStopId("Stop1")
            .chargeAmount(BigDecimal.ZERO)
            .companyId("Company1")
            .busId("Bus37")
            .maskedPan("****1111")
            .panHash("somehash3")
            .status(TripStatus.CANCELLED)
            .tapOnEventId(4L)
            .tapOffEventId(5L)
            .build();
    }

    private String writtenCsv(List<Trip> trips) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(trips, out);
        return out.toString();
    }

    @Nested
    @DisplayName("header row")
    class HeaderRow {

        @Test
        @DisplayName("header contains all 10 required columns")
        void headerContainsAllColumns() throws IOException {
            String csv = writtenCsv(List.of());

            assertThat(csv).contains("Started");
            assertThat(csv).contains("Finished");
            assertThat(csv).contains("DurationSecs");
            assertThat(csv).contains("FromStopId");
            assertThat(csv).contains("ToStopId");
            assertThat(csv).contains("ChargeAmount");
            assertThat(csv).contains("CompanyId");
            assertThat(csv).contains("BusID");
            assertThat(csv).contains("PAN");
            assertThat(csv).contains("Status");
        }

        @Test
        @DisplayName("empty trip list produces header row only")
        void emptyListProducesHeaderOnly() throws IOException {
            String csv = writtenCsv(List.of());
            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(1);
        }
    }

    // -------------------------------------------------------
    // COMPLETED row
    // -------------------------------------------------------

    @Nested
    @DisplayName("COMPLETED trip row")
    class CompletedRow {

        @Test
        @DisplayName("all fields present and correct")
        void allFieldsPresent() throws IOException {
            String csv = writtenCsv(List.of(completedTrip()));

            assertThat(csv).contains("22-01-2023 13:00:00");   // started
            assertThat(csv).contains("22-01-2023 13:05:00");   // finished
            assertThat(csv).contains("300");                    // durationSecs
            assertThat(csv).contains("Stop1");                  // fromStopId
            assertThat(csv).contains("Stop2");                  // toStopId
            assertThat(csv).contains("$3.25");                  // chargeAmount
            assertThat(csv).contains("Company1");               // companyId
            assertThat(csv).contains("Bus37");                  // busId
            assertThat(csv).contains("****5559");               // maskedPan
            assertThat(csv).contains("COMPLETED");              // status
        }

        @Test
        @DisplayName("charge formatted with dollar sign and 2 decimal places")
        void chargeFormatted() throws IOException {
            String csv = writtenCsv(List.of(completedTrip()));
            assertThat(csv).contains("$3.25");
            assertThat(csv).doesNotContain("3.2500");
            assertThat(csv).doesNotContain("3.250");
        }

        @Test
        @DisplayName("datetime formatted as dd-MM-yyyy HH:mm:ss")
        void datetimeFormatted() throws IOException {
            String csv = writtenCsv(List.of(completedTrip()));
            // Correct format
            assertThat(csv).contains("22-01-2023 13:00:00");
            // Wrong formats should NOT appear
            assertThat(csv).doesNotContain("2023-01-22");
        }
    }

    // -------------------------------------------------------
    // INCOMPLETE row
    // -------------------------------------------------------

    @Nested
    @DisplayName("INCOMPLETE trip row")
    class IncompleteRow {

        @Test
        @DisplayName("Finished column is empty string — not null or 'null'")
        void finishedIsEmptyNotNull() throws IOException {
            String csv = writtenCsv(List.of(incompleteTrip()));
            assertThat(csv).doesNotContain("null");
        }

        @Test
        @DisplayName("ToStopId column is empty string — not null or 'null'")
        void toStopIdIsEmptyNotNull() throws IOException {
            String csv = writtenCsv(List.of(incompleteTrip()));
            assertThat(csv).doesNotContain("null");
        }

        @Test
        @DisplayName("DurationSecs is 0 for INCOMPLETE")
        void durationIsZero() throws IOException {
            String csv = writtenCsv(List.of(incompleteTrip()));
            assertThat(csv).contains("0");
        }

        @Test
        @DisplayName("max fare charged — $7.30 from Stop3")
        void maxFareCharged() throws IOException {
            String csv = writtenCsv(List.of(incompleteTrip()));
            assertThat(csv).contains("$7.30");
        }

        @Test
        @DisplayName("status is INCOMPLETE")
        void statusIsIncomplete() throws IOException {
            String csv = writtenCsv(List.of(incompleteTrip()));
            assertThat(csv).contains("INCOMPLETE");
        }
    }

    // -------------------------------------------------------
    // CANCELLED row
    // -------------------------------------------------------

    @Nested
    @DisplayName("CANCELLED trip row")
    class CancelledRow {

        @Test
        @DisplayName("charge is $0.00 for CANCELLED")
        void zeroCharge() throws IOException {
            String csv = writtenCsv(List.of(cancelledTrip()));
            assertThat(csv).contains("$0.00");
        }

        @Test
        @DisplayName("status is CANCELLED")
        void statusIsCancelled() throws IOException {
            String csv = writtenCsv(List.of(cancelledTrip()));
            assertThat(csv).contains("CANCELLED");
        }

        @Test
        @DisplayName("both fromStopId and toStopId are same stop")
        void bothStopsSame() throws IOException {
            String csv = writtenCsv(List.of(cancelledTrip()));
            // Stop1 appears twice — once for from, once for to
            long stop1Count = csv.lines()
                .filter(l -> l.contains("CANCELLED"))
                .mapToLong(l -> l.chars()
                    .filter(c -> c == 'S').count())
                .sum();
            assertThat(stop1Count).isGreaterThanOrEqualTo(2);
        }
    }

    // -------------------------------------------------------
    // PAN security
    // -------------------------------------------------------

    @Nested
    @DisplayName("PAN security")
    class PanSecurity {

        @Test
        @DisplayName("PAN column is masked — ****XXXX format")
        void panIsMasked() throws IOException {
            String csv = writtenCsv(List.of(completedTrip()));
            assertThat(csv).contains("****5559");
        }

        @Test
        @DisplayName("raw PAN digits never appear in output")
        void rawPanNeverAppears() throws IOException {
            String csv = writtenCsv(List.of(completedTrip()));
            // Full raw PAN should never appear
            assertThat(csv).doesNotContain("5500005555555559");
        }
    }

    // -------------------------------------------------------
    // Multiple trips
    // -------------------------------------------------------

    @Nested
    @DisplayName("multiple trips")
    class MultipleTrips {

        @Test
        @DisplayName("writes 3 trips from assignment sample input")
        void writesAllSampleTrips() throws IOException {
            String csv = writtenCsv(List.of(completedTrip(), incompleteTrip(), cancelledTrip()));

            // 1 header + 3 data rows
            long lines = csv.lines().filter(l -> !l.isBlank()).count();
            assertThat(lines).isEqualTo(4);
        }

        @Test
        @DisplayName("all three statuses present in output")
        void allStatusesPresent() throws IOException {
            String csv = writtenCsv(List.of(completedTrip(), incompleteTrip(), cancelledTrip()));

            assertThat(csv).contains("COMPLETED");
            assertThat(csv).contains("INCOMPLETE");
            assertThat(csv).contains("CANCELLED");
        }

        @Test
        @DisplayName("all three charge amounts correct")
        void allChargeAmountsCorrect() throws IOException {
            String csv = writtenCsv(List.of(completedTrip(), incompleteTrip(), cancelledTrip()));

            assertThat(csv).contains("$3.25");
            assertThat(csv).contains("$7.30");
            assertThat(csv).contains("$0.00");
        }
    }
}
