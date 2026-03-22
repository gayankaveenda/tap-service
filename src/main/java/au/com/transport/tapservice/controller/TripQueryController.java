package au.com.transport.tapservice.controller;

import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import au.com.transport.tapservice.repository.trip.TripRepository;
import au.com.transport.tapservice.service.trip.TripOutputWriterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
@Tag(name = "Trips", description = "Query resolved trips")
public class TripQueryController {

    private final TripRepository tripRepository;
    private final TripOutputWriterService tripOutputWriterService;

    @GetMapping("/export")
    @Operation(
            summary = "Export trips as CSV",
            description = """
                    Downloads resolved trips in CSV format. Optional query parameters allow filtering by status and start time range.
                    
                    Query parameters:
                    - status: filter by trip status (e.g. COMPLETED, INCOMPLETE)
                    - startedFrom: filter trips that started on or after this date-time (ISO format)
                    - startedTo: filter trips that started on or before this date-time (ISO format)
                    Date Example: 2024-01-01T00:00:00
                    If no parameters are provided, all trips are exported.
                    """
    )
    public void exportCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startedTo,
            HttpServletResponse response
    ) throws IOException {

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"trips.csv\"");

        TripStatus tripStatus = null;

        if (status != null && !status.isBlank()) {
            tripStatus = TripStatus.valueOf(status.toUpperCase());
        }

        List<Trip> trips;

        if (tripStatus == null && startedFrom == null && startedTo == null) {
            trips = tripRepository.findAllByOrderByStartedAsc();
        } else if (tripStatus != null && startedFrom == null && startedTo == null) {
            trips = tripRepository.findByStatusOrderByStartedAsc(tripStatus);
        } else if (tripStatus == null) {
            trips = tripRepository.findByStartedBetweenOrderByStartedAsc(startedFrom, startedTo);
        } else {
            trips = tripRepository.findByStatusAndStartedBetweenOrderByStartedAsc(
                    tripStatus, startedFrom, startedTo
            );
        }

        tripOutputWriterService.write(trips, response.getOutputStream());
    }
}
