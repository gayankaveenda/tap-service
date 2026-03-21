package au.com.transport.tapservice.service;

import au.com.transport.tapservice.entity.ParsedRow;
import lombok.Builder;
import lombok.Getter;

import java.io.InputStream;
import java.util.List;

public interface ICsvParser {

    /**
     * Parses the given CSV input stream and returns a stream of TapRecord objects.
     * The stream is lazy and must be closed by the caller after use.
     *
     * @param inputStream the CSV data as an InputStream
     * @param sourceFile  the name of the source file (for error reporting)
     * @return a Stream of TapRecord objects
     */
    IngestionRecord parse(InputStream inputStream, String sourceFile);

    /**
     * Summary returned to the caller after ingesting a CSV file.
     */
    @Getter
    @Builder
    class IngestionRecord {

        private final String sourceFile;
        private final List<ParsedRow> successfulRecords;
        private final List<ParsedRow> failures;

    }
}
