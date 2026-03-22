package au.com.transport.tapservice.mapper;

import au.com.transport.tapservice.config.PanTokeniser;
import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.entity.ingestion.TapRecordCsv;
import au.com.transport.tapservice.entity.ingestion.TapType;
import au.com.transport.tapservice.exception.InvalidTapDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TapRecordMapper")
class TapRecordMapperTest {

    private TapRecordMapper mapper;

    private static final String SOURCE_FILE = "taps.csv";
    private static final int ROW_NUMBER = 1;
    private static final String VALID_PAN = "5500005555555559";
    private static final String VALID_DATE = "22-01-2023 13:00:00";

    @BeforeEach
    void setUp() {
        PanTokeniser panTokeniser = new PanTokeniser("test-salt");
        mapper = new TapRecordMapper(panTokeniser);
    }

    private TapRecordCsv validCsv() {
        TapRecordCsv csv = new TapRecordCsv();
        csv.setId(1L);
        csv.setDateTimeUTC(VALID_DATE);
        csv.setTapType("ON");
        csv.setStopId("Stop1");
        csv.setCompanyId("Company1");
        csv.setBusId("Bus37");
        csv.setPan(VALID_PAN);
        return csv;
    }

    @Nested
    @DisplayName("valid CSV row")
    class ValidMapping {

        @Test
        @DisplayName("maps originalId correctly from CSV ID")
        void mapsOriginalId() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getOriginalId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("maps tappedAt correctly from DateTimeUTC")
        void mapsTappedAt() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getTappedAt())
                    .isEqualTo(LocalDateTime.of(2023, 1, 22, 13, 0, 0));
        }

        @Test
        @DisplayName("maps TapType ON correctly")
        void mapsTapTypeOn() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getTapType()).isEqualTo(TapType.ON);
        }

        @Test
        @DisplayName("maps TapType OFF correctly")
        void mapsTapTypeOff() {
            TapRecordCsv csv = validCsv();
            csv.setTapType("OFF");
            TapEvent event = mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getTapType()).isEqualTo(TapType.OFF);
        }

        @Test
        @DisplayName("maps stopId, companyId and busId correctly")
        void mapsLocationFields() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getStopId()).isEqualTo("Stop1");
            assertThat(event.getCompanyId()).isEqualTo("Company1");
            assertThat(event.getBusId()).isEqualTo("Bus37");
        }

        @Test
        @DisplayName("raw PAN is never stored — panHash and maskedPan set instead")
        void rawPanNeverStored() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            // No getter for raw pan — this is by design
            assertThat(event.getPanHash()).isNotNull().hasSize(64);
            assertThat(event.getMaskedPan()).isEqualTo("****5559");
        }

        @Test
        @DisplayName("panHash is deterministic — same PAN produces same hash")
        void panHashIsDeterministic() {
            TapEvent e1 = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            TapEvent e2 = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(e1.getPanHash()).isEqualTo(e2.getPanHash());
        }

        @Test
        @DisplayName("different PANs produce different panHashes")
        void differentPansDifferentHashes() {
            TapRecordCsv csv1 = validCsv();
            TapRecordCsv csv2 = validCsv();
            csv2.setPan("4111111111111111");

            TapEvent e1 = mapper.toDomain(csv1, SOURCE_FILE, ROW_NUMBER);
            TapEvent e2 = mapper.toDomain(csv2, SOURCE_FILE, ROW_NUMBER);

            assertThat(e1.getPanHash()).isNotEqualTo(e2.getPanHash());
        }

        @Test
        @DisplayName("status defaults to PENDING after mapping")
        void statusDefaultsPending() {
            TapEvent event = mapper.toDomain(validCsv(), SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getStatus()).isEqualTo(TapEvent.TapEventStatus.PENDING);
        }

        @Test
        @DisplayName("whitespace in stopId is trimmed via normalize()")
        void trimsStopId() {
            TapRecordCsv csv = validCsv();
            csv.setStopId(" Stop1 ");
            TapEvent event = mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getStopId()).isEqualTo("Stop1");
        }

        @Test
        @DisplayName("whitespace in busId is trimmed via normalize()")
        void trimsBusId() {
            TapRecordCsv csv = validCsv();
            csv.setBusId(" Bus37 ");
            TapEvent event = mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getBusId()).isEqualTo("Bus37");
        }

        @Test
        @DisplayName("tapType is case-insensitive — lowercase accepted")
        void tapTypeCaseInsensitive() {
            TapRecordCsv csv = validCsv();
            csv.setTapType("on");
            TapEvent event = mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getTapType()).isEqualTo(TapType.ON);
        }

        @Test
        @DisplayName("tapType with surrounding spaces accepted")
        void tapTypeWithSpaces() {
            TapRecordCsv csv = validCsv();
            csv.setTapType(" OFF ");
            TapEvent event = mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER);
            assertThat(event.getTapType()).isEqualTo(TapType.OFF);
        }
    }

    @Nested
    @DisplayName("PAN validation")
    class PanValidation {

        @Test
        @DisplayName("non-numeric PAN throws InvalidTapDataException")
        void nonNumericPanThrows() {
            TapRecordCsv csv = validCsv();
            csv.setPan("INVALIDPAN");

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class)
                    .hasMessageContaining("PAN");
        }

        @Test
        @DisplayName("null PAN throws InvalidTapDataException")
        void nullPanThrows() {
            TapRecordCsv csv = validCsv();
            csv.setPan(null);

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class);
        }

        @Test
        @DisplayName("blank PAN throws InvalidTapDataException")
        void blankPanThrows() {
            TapRecordCsv csv = validCsv();
            csv.setPan("   ");

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class);
        }

        @Test
        @DisplayName("alphanumeric PAN throws InvalidTapDataException")
        void alphanumericPanThrows() {
            TapRecordCsv csv = validCsv();
            csv.setPan("4111ABC1111111");

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class);
        }

        @Test
        @DisplayName("error message includes source file and row number")
        void errorMessageIncludesContext() {
            TapRecordCsv csv = validCsv();
            csv.setPan("INVALIDPAN");

            assertThatThrownBy(() -> mapper.toDomain(csv, "taps.csv", 5))
                    .isInstanceOf(InvalidTapDataException.class)
                    .hasMessageContaining("taps.csv")
                    .hasMessageContaining("5");
        }
    }

    @Nested
    @DisplayName("datetime validation")
    class DateTimeValidation {

        @Test
        @DisplayName("invalid datetime format throws InvalidTapDataException")
        void invalidDatetimeThrows() {
            TapRecordCsv csv = validCsv();
            csv.setDateTimeUTC("22-01-2023 XX:05:00");

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class)
                    .hasMessageContaining("datetime");
        }

        @Test
        @DisplayName("empty datetime throws InvalidTapDataException")
        void emptyDatetimeThrows() {
            TapRecordCsv csv = validCsv();
            csv.setDateTimeUTC("");

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "2023-01-22 13:00:00",   // wrong date format
                "22/01/2023 13:00:00",   // wrong separator
                "22-01-2023",            // missing time
                "13:00:00"               // missing date
        })
        @DisplayName("various wrong formats throw InvalidTapDataException")
        void wrongFormatsThrow(String badDate) {
            TapRecordCsv csv = validCsv();
            csv.setDateTimeUTC(badDate);

            assertThatThrownBy(() -> mapper.toDomain(csv, SOURCE_FILE, ROW_NUMBER))
                    .isInstanceOf(InvalidTapDataException.class);
        }
    }
}
