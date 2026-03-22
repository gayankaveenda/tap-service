package au.com.transport.tapservice.service.trip;

import au.com.transport.tapservice.entity.trip.Trip;
import com.opencsv.CSVWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class TripOutputWriterService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final String[] HEADERS = {
            "Started", "Finished", "DurationSecs", "FromStopId", "ToStopId",
            "ChargeAmount", "CompanyId", "BusID", "PAN", "Status"
    };

    /**
     * Write trips to the given output stream in CSV format.
     * Stream is NOT closed — caller is responsible.
     */
    public void write(List<Trip> trips, OutputStream outputStream) throws IOException {
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            writer.writeNext(HEADERS);

            for (Trip trip : trips) {
                writer.writeNext(toRow(trip));
            }

            writer.flush();
        }
        log.info("trips.csv written: {} rows", trips.size());
    }

    private String[] toRow(Trip trip) {
        return new String[]{
                trip.getStarted() != null
                        ? trip.getStarted().format(FORMATTER) : "",

                trip.getFinished() != null
                        ? trip.getFinished().format(FORMATTER) : "",

                String.valueOf(trip.getDurationSecs()),

                trip.getFromStopId() != null ? trip.getFromStopId() : "",

                trip.getToStopId() != null ? trip.getToStopId() : "",

                trip.getChargeAmount() != null
                        ? "$" + trip.getChargeAmount()
                        .setScale(2, RoundingMode.HALF_UP)
                        .toPlainString()
                        : "$0.00",

                trip.getCompanyId() != null ? trip.getCompanyId() : "",

                trip.getBusId() != null ? trip.getBusId() : "",

                trip.getMaskedPan() != null ? trip.getMaskedPan() : "",

                trip.getStatus() != null ? trip.getStatus().name() : ""
        };
    }
}
