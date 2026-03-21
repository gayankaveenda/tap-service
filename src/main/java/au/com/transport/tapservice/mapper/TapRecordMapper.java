package au.com.transport.tapservice.mapper;

import au.com.transport.tapservice.config.PanTokeniser;
import au.com.transport.tapservice.entity.TapEvent;
import au.com.transport.tapservice.entity.TapRecordCsv;
import au.com.transport.tapservice.entity.TapType;
import au.com.transport.tapservice.exception.InvalidTapDataException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
@RequiredArgsConstructor
public class TapRecordMapper {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final PanTokeniser panTokeniser;

    public TapEvent toDomain(TapRecordCsv csv, String sourceFile, int rowNumber) {

        TapEvent record = new TapEvent();

        record.setOriginalId(csv.getId());
        record.setTappedAt(parseDateTime(csv.getDateTimeUTC(), sourceFile, rowNumber));
        record.setTapType(getTapType(csv, sourceFile, rowNumber));
        record.setStopId(csv.getStopId());
        record.setCompanyId(csv.getCompanyId());
        record.setBusId(csv.getBusId());

        //do i need to do a null check?
        //TODO: I need to remove raw pan later
        record.setPan(csv.getPan());

        // Hash and mask immediately — raw PAN is discarded after this
        String panHash = panTokeniser.hash(csv.getPan());
        String maskedPan = panTokeniser.mask(csv.getPan());

        record.setPanHash(panHash);
        record.setMaskedPan(maskedPan);

        return record;
    }

    private @NonNull TapType getTapType(TapRecordCsv csv, String sourceFile, int rowNumber) {
        try {
            return TapType.valueOf(csv.getTapType().trim().toUpperCase());
        } catch (InvalidTapDataException e) {
            throw new InvalidTapDataException(
                    "File Name: %s, Row %d: Invalid TapType: %s"
                            .formatted(sourceFile, rowNumber, csv.getTapType())
            );
        }
    }

    private @NonNull LocalDateTime parseDateTime(String value, String sourceFile, int rowNumber) {
        try {
            return LocalDateTime.parse(value.trim(), FORMATTER);
        } catch (DateTimeParseException e) {
            throw new InvalidTapDataException(
                    "File Name: %s, Row %d: datetime '%s' does not match format 'dd-MM-yyyy HH:mm:ss'"
                            .formatted(sourceFile, rowNumber, value));
        }
    }

}