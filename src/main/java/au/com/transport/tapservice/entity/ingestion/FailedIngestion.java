package au.com.transport.tapservice.entity.ingestion;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "failed_ingestion_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedIngestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_row", nullable = false, columnDefinition = "TEXT")
    private String rawRow;              // Original CSV line for replay

    @Column(name = "row_number")
    private Integer rowNumber;          // Line number in file for easy debugging

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "source_file")
    private String sourceFile;          // Which file this came from

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
