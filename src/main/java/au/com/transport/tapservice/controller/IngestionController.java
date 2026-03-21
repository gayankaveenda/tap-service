package au.com.transport.tapservice.controller;

import au.com.transport.tapservice.dto.IngestionResponse;
import au.com.transport.tapservice.service.IngestionService;
import au.com.transport.tapservice.util.IngestionSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "CSV tap event ingestion")
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload tap event CSV file",
            description = "File to be processed will process ignoring invalid rows. Returns a summary of the ingestion result." +
                    "All failed rows will also be saved to the database for later review and reprocessing.")
    public ResponseEntity<IngestionResponse> ingest(
            @RequestParam("file") MultipartFile file) {

        if (file == null) {
            return ResponseEntity.badRequest()
                    .body(IngestionResponse.error("No input file provided"));
        }

        IngestionSummary ingestionSummary = processFile(file);

        boolean anySuccess = ingestionSummary.getSavedRows() > 0;
        boolean anyFailure = ingestionSummary.hasFailures();

        int status = (!anySuccess) ? 422 : (anyFailure ? 207 : 200);

        return ResponseEntity.status(status).body(IngestionResponse.from(List.of(ingestionSummary)));
    }

    private IngestionSummary processFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        try {
            return ingestionService.ingest(file.getInputStream(), filename);
        } catch (IOException e) {
            log.error("Cannot read file: {}", filename, e);
            return IngestionSummary.builder()
                    .sourceFile(filename)
                    .savedRows(0).skippedRows(0).failedRows(1)
                    .failures(List.of(new IngestionSummary.RowFailure(
                            0, "", "File unreadable: " + e.getMessage())))
                    .build();
        }
    }
}
