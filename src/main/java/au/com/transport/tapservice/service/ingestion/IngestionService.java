package au.com.transport.tapservice.service.ingestion;

import au.com.transport.tapservice.entity.ingestion.FailedIngestion;
import au.com.transport.tapservice.entity.ingestion.ParsedRow;
import au.com.transport.tapservice.entity.ingestion.TapEvent;
import au.com.transport.tapservice.repository.ingestion.FailedIngestionRepository;
import au.com.transport.tapservice.repository.ingestion.TapEventRepository;
import au.com.transport.tapservice.entity.ingestion.IngestionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final TapRecordParserService csvParser;
    private final TapEventRepository tapEventRepository;
    private final FailedIngestionRepository failedIngestionRepository;

    @Value("${app.ingestion.batch-size}")
    private int batchSize;

    public IngestionSummary ingest(InputStream inputStream, String sourceFile) {
        log.info("Ingestion started: file={}", sourceFile);

        List<TapEvent> successBatch = new ArrayList<>();
        List<FailedIngestion> failedBatch = new ArrayList<>();

        int savedRows = 0;
        int skippedRows = 0;
        int failedRows = 0;

        ICsvParser.IngestionRecord ingestionRecord = csvParser.parse(inputStream, sourceFile);

        //get a copy of failures to add any new ones during saving
        List<ParsedRow> failedRecords = new ArrayList<>(List.copyOf(ingestionRecord.getFailures()));

        //convert existing failures to FailedIngestionRecord for batch saving
        for (ParsedRow failedRecord : ingestionRecord.getFailures()) {
            failedRows += processFailedBatch(failedRecord.rawRow(), failedRecord, new Exception(failedRecord.failureReason()), failedBatch);
        }

        for (ParsedRow parsedRow : ingestionRecord.getSuccessfulRecords()) {

            try {
                // Idempotency — skip if already ingested
                if (tapEventRepository.existsByOriginalIdAndTappedAtAndPanHash(
                        parsedRow.tapRecord().getOriginalId(),
                        parsedRow.tapRecord().getTappedAt(),
                        parsedRow.tapRecord().getPanHash()
                )) {
                    log.debug("Skipping duplicate originalId={}, tappedAt={}, panHash={}",
                            parsedRow.tapRecord().getOriginalId(),
                            parsedRow.tapRecord().getTappedAt(),
                            parsedRow.tapRecord().getPanHash());
                    skippedRows++;
                    continue;
                }

                successBatch.add(parsedRow.tapRecord());

                if (successBatch.size() >= batchSize) {
                    savedRows += flushSuccessfulBatch(successBatch);
                    successBatch.clear();
                }
            } catch (Exception e) {
                log.error("Failed to save record: file={}, line={}, error={}", sourceFile, parsedRow.rawRow(), e.getMessage());
                failedRecords.add(
                        ParsedRow.failure(parsedRow.rawRow(), parsedRow.rowNumber(), e.getMessage())
                );

                failedRows += processFailedBatch(sourceFile, parsedRow, e, failedBatch);
            }
        }

        // Flush remaining tail
        if (!successBatch.isEmpty()) {
            log.debug("Flushing final batch of {} records for file={}", successBatch.size(), sourceFile);
            savedRows += flushSuccessfulBatch(successBatch);
        }

        if (!failedBatch.isEmpty()) {
            log.info("Flushing final batch of {} failed records for file={}", failedBatch.size(), sourceFile);
            failedRows += flushFailureBatch(failedBatch);
        }

        log.info("Ingestion complete: file={}, saved={}, skipped={}, failed={}",
                sourceFile, savedRows, skippedRows, failedRows);

        //convert failedRecords to RowFailure
        List<IngestionSummary.RowFailure> rowFailures = new ArrayList<>();
        for (ParsedRow failedRecord : failedRecords) {
            rowFailures.add(new IngestionSummary.RowFailure(
                    failedRecord.rowNumber(),
                    failedRecord.rawRow(),
                    failedRecord.failureReason()
            ));
        }

        return IngestionSummary.builder()
                .sourceFile(sourceFile)
                .successfulRecords(List.copyOf(ingestionRecord.getSuccessfulRecords()))
                .savedRows(savedRows)
                .skippedRows(skippedRows)
                .failedRows(failedRows)
                .failures(List.copyOf(rowFailures))
                .build();

    }

    private int processFailedBatch(String sourceFile, ParsedRow parsedRow, Exception e, List<FailedIngestion> failedBatch) {
        int savedRows = 0;
        FailedIngestion failure = createFailedIngestionRecord(sourceFile, parsedRow, e);
        failedBatch.add(failure);

        if (failedBatch.size() >= batchSize) {
            log.debug("Flushing {} failed records for file={}", failedBatch.size(), sourceFile);
            savedRows += flushFailureBatch(failedBatch);
            failedBatch.clear();
        }
        return savedRows;
    }

    private static @NonNull FailedIngestion createFailedIngestionRecord(String sourceFile, ParsedRow parsedRow, Exception e) {
        FailedIngestion failure = new FailedIngestion();
        failure.setSourceFile(sourceFile);
        failure.setRowNumber(parsedRow.rowNumber());
        failure.setRawRow(parsedRow.rawRow());
        failure.setFailureReason(e.getMessage());
        return failure;
    }

    protected int flushSuccessfulBatch(List<TapEvent> batch) {
        tapEventRepository.saveAll(batch);
        log.debug("Batch saved: size={}", batch.size());
        return batch.size();
    }

    protected int flushFailureBatch(List<FailedIngestion> batch) {
        failedIngestionRepository.saveAll(batch);
        log.debug("Batch saved: size={}", batch.size());
        return batch.size();
    }
}
