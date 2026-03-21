package au.com.transport.tapservice.util;

import au.com.transport.tapservice.entity.ParsedRow;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IngestionSummary {

    private final String sourceFile;
    private final int savedRows;
    private final int skippedRows;
    private final int failedRows;
    private final List<ParsedRow> successfulRecords;
    private final List<RowFailure> failures;

    public record RowFailure(int rowNumber, String rawRow, String reason) {
    }

    public boolean hasFailures() {
        return failedRows > 0;
    }

    public boolean isFullSuccess() {
        return failedRows == 0 && savedRows > 0;
    }

}
