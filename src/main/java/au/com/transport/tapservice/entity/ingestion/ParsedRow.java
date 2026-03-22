package au.com.transport.tapservice.entity.ingestion;

/**
 * Represents the result of parsing a single CSV row into a TapEvent.
 * Contains both the original raw row and the parsed TapEvent (if successful).
 * Also includes metadata about the row number and any failure reason if parsing failed.
 */
public record ParsedRow(
        TapEvent tapRecord,          // non-null if success
        String rawRow,              // original CSV line always preserved
        int rowNumber,
        String failureReason,       // non-null if failure
        boolean isValid
) {
    public static ParsedRow success(TapEvent tapRecord, String rawRow, int rowNumber) {
        return new ParsedRow(tapRecord, rawRow, rowNumber, null, true);
    }

    public static ParsedRow failure(String rawRow, int rowNumber, String reason) {
        return new ParsedRow(null, rawRow, rowNumber, reason, false);
    }
}