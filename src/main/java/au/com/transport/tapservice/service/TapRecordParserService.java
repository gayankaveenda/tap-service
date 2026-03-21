package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.ParsedRow;
import au.com.transport.tapservice.entity.TapEvent;
import au.com.transport.tapservice.entity.TapRecordCsv;
import au.com.transport.tapservice.mapper.TapRecordMapper;
import com.opencsv.CSVReader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class TapRecordParserService implements ICsvParser {

    private final TapRecordMapper tapRecordMapper;

    public TapRecordParserService(TapRecordMapper tapRecordMapper) {
        this.tapRecordMapper = tapRecordMapper;
    }

    @Override
    public IngestionRecord parse(InputStream inputStream, String sourceFile) {

        List<ParsedRow> successfulRecords = new ArrayList<>();
        List<ParsedRow> failedRecords = new ArrayList<>();

        parseCsvFile(inputStream, sourceFile, successfulRecords, failedRecords);
        return IngestionRecord.builder()
                .sourceFile(sourceFile)
                .successfulRecords(successfulRecords)
                .failures(failedRecords)
                .build();
    }

    private void parseCsvFile(InputStream inputStream, String sourceFile, List<ParsedRow> successfulRecords, List<ParsedRow> failedRecords) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {

            String[] line;
            int rowNum = 0;

            while ((line = reader.readNext()) != null) {

                if (rowNum == 0) {
                    rowNum++;
                    continue; // skip header
                }

                try {
                    TapRecordCsv csv = mapLine(line); // still use CsvToBean-like mapping logic
                    TapEvent event = tapRecordMapper.toDomain(csv, sourceFile, rowNum);

                    successfulRecords.add(
                            ParsedRow.success(event, Arrays.toString(line), rowNum)
                    );

                } catch (Exception e) {
                    failedRecords.add(
                            ParsedRow.failure(Arrays.toString(line), rowNum, e.getMessage())
                    );
                }
                rowNum++;
            }

        } catch (Exception e) {
            failedRecords.add(
                    ParsedRow.failure("File-level error", 0, e.getMessage())
            );
        }
    }


    private TapRecordCsv mapLine(String[] line) {

        if (line.length < 7) {
            throw new IllegalArgumentException("Invalid column count: " + line.length);
        }

        TapRecordCsv csv = new TapRecordCsv();

        try {
            csv.setId(Long.valueOf(line[0]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID: " + line[0]);
        }

        csv.setDateTimeUTC(line[1]);
        csv.setTapType(line[2]);
        csv.setStopId(line[3]);
        csv.setCompanyId(line[4]);
        csv.setBusId(line[5]);
        csv.setPan(line[6]);

        return csv;
    }
}
