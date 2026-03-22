package au.com.transport.tapservice.entity.ingestion;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents a TAP event (TAP ON or TAP OFF) as ingested from the CSV file.
 * Each record corresponds to one line in the input CSV.
 * <p>
 * The original PAN is stored for reference but is not used for matching.
 * Instead, a salted hash of the PAN is used to link TAP ON and TAP OFF events.
 * This design ensures that we can match trips without exposing sensitive card information.
 */
@Entity
@Table(name = "tap_events", indexes = {
        @Index(name = "idx_tap_events_pan_hash_bus", columnList = "pan_hash, bus_id, status"),
        @Index(name = "idx_tap_events_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_id", nullable = false)
    private Long originalId;                // ID from CSV

    @Column(name = "tapped_at", nullable = false)
    private LocalDateTime tappedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "tap_type", nullable = false, length = 3)
    private TapType tapType;

    @Column(name = "stop_id", nullable = false, length = 20)
    private String stopId;

    @Column(name = "company_id", nullable = false, length = 50)
    private String companyId;

    @Column(name = "bus_id", nullable = false, length = 50)
    private String busId;

//    /**
//     * Primary Account Number — the card identifier.
//     * Stored as-is; masking happens at the API response layer.
//     */
//    @Column(name = "pan", nullable = false, length = 50)
//    private String pan;

    /**
     * SHA-256 hash of (pan + salt).
     * Used for matching TAP ON to TAP OFF.
     * Cannot be reversed to the original PAN.
     */
    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;

    /**
     * Last 4 digits masked: ****5559
     * Used only for display in output CSV and API responses.
     */
    @Column(name = "masked_pan", nullable = false, length = 20)
    private String maskedPan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TapEventStatus status = TapEventStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum TapEventStatus {
        PENDING,      // Saved, not yet processed
        PROCESSED,    // Successfully matched into a trip
        UNMATCHED,    // TAP OFF with no preceding TAP ON — logged, skipped
        CANCELED_DUPLICATE,      // Manually marked as canceled — will not process
        INVALID,      // Invalid data (e.g. negative trip duration) — logged, skipped
        FAILED        // Processing error — will retry
    }
}
