package au.com.transport.tapservice.dto;

import au.com.transport.tapservice.entity.ingestion.IngestionSummary;

import java.util.List;

/**
 * Response returned to the caller after ingesting one or more CSV files.
 * Provides an overall summary of the ingestion process, including totals and any errors.
 */
public record IngestionResponse(
        int totalFiles,
        int totalSaved,
        int totalFailed,
        int totalDuplicates,
        boolean isFullySuccessful,
        boolean isPartiallySuccessful,
        String error
) {
    public static IngestionResponse from(List<IngestionSummary> summaries) {
        return new IngestionResponse(
                summaries.size(),
                summaries.stream().mapToInt(IngestionSummary::getSavedRows).sum(),
                summaries.stream().mapToInt(IngestionSummary::getFailedRows).sum(),
                summaries.stream().mapToInt(IngestionSummary::getSkippedRows).sum(),
                summaries.stream().allMatch(IngestionSummary::isFullSuccess),
                summaries.stream().anyMatch(IngestionSummary::isFullSuccess) && summaries.stream().anyMatch(s -> !s.isFullSuccess()),
                null
        );
    }

    public static IngestionResponse error(String msg) {
        return new IngestionResponse(0, 0, 0, 0, false, false, msg);
    }
}