package au.com.transport.tapservice.entity;

import com.opencsv.bean.CsvBindByPosition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single row of the input CSV file for tap records.
 * This class is used by OpenCSV to map CSV columns to Java fields.
 * It includes validation annotations to ensure data integrity before processing.
 */
@Data
@NoArgsConstructor
public class TapRecordCsv {

    @NotNull
    @Positive
    @CsvBindByPosition(position = 0)
    private Long id;

    @CsvBindByPosition(position = 1)
    private String dateTimeUTC;

    @NotNull
    @CsvBindByPosition(position = 2)
    private String tapType;

    @NotNull
    @CsvBindByPosition(position = 3)
    private String stopId;

    @NotNull
    @CsvBindByPosition(position = 4)
    private String companyId;

    @NotNull
    @CsvBindByPosition(position = 5)
    private String busId;

    @NotNull
    @CsvBindByPosition(position = 6)
    private String pan;

}
