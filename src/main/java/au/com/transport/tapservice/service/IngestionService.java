package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.ParsedRow;
import au.com.transport.tapservice.entity.TapEvent;
import au.com.transport.tapservice.util.IngestionSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final TapRecordParserService csvParser;
//    private final TapEventRepository tapEventRepository;

    @Value("${app.ingestion.batch-size:2}")
    private int batchSize;

    @Transactional
    public IngestionSummary ingest(InputStream inputStream, String sourceFile) {
        log.info("Ingestion started: file={}", sourceFile);

        List<TapEvent> batch = new ArrayList<>();
        int savedRows = 0;
        int skippedRows = 0;

        ICsvParser.IngestionRecord ingestionRecord = csvParser.parse(inputStream, sourceFile);

        //get a copy of failures to add any new ones during saving
        List<ParsedRow> failedRecords = new ArrayList<>(List.copyOf(ingestionRecord.getFailures()));

        for (ParsedRow parsedRow : ingestionRecord.getSuccessfulRecords()) {

            try {
                // Idempotency — skip if already ingested
//            if (tapEventRepository.existsByOriginalId(result.tapEvent().getOriginalId())) {
//                log.debug("Skipping duplicate originalId={}", result.tapEvent().getOriginalId());
//                skippedRows++;
//                continue;
//            }

                batch.add(parsedRow.tapRecord());

                if (batch.size() >= batchSize) {
                    savedRows += flushBatch(batch);
                    batch.clear();
                }
            } catch (Exception e) {
                log.error("Failed to save record: file={}, line={}, error={}", sourceFile, parsedRow.rawRow(), e.getMessage());
                failedRecords.add(
                        ParsedRow.failure(parsedRow.rawRow(), parsedRow.rowNumber(), e.getMessage())
                );
            }
        }

        // Flush remaining tail
        if (!batch.isEmpty()) {
            savedRows += flushBatch(batch);
        }

        log.info("Ingestion complete: file={}, saved={}, skipped={}, failed={}",
                sourceFile, savedRows, skippedRows, ingestionRecord.getFailures().size());

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
                .failedRows(failedRecords.size())
                .failures(List.copyOf(rowFailures))
                .build();

    }

    @Transactional
    protected int flushBatch(List<TapEvent> batch) {
//        tapEventRepository.saveAll(batch);
        log.debug("Batch saved: size={}", batch.size());
        return batch.size();
    }
}
