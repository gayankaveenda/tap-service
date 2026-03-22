package au.com.transport.tapservice.entity.trip;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_status", columnList = "status"),
        @Index(name = "idx_trips_masked_pan", columnList = "masked_pan")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started", nullable = false)
    private LocalDateTime started;              // TAP ON datetime

    @Column(name = "finished")
    private LocalDateTime finished;             // TAP OFF datetime — null for INCOMPLETE

    @Column(name = "duration_secs", nullable = false)
    @Builder.Default
    private long durationSecs = 0L;            // 0 for INCOMPLETE

    @Column(name = "from_stop_id", nullable = false, length = 20)
    private String fromStopId;

    @Column(name = "to_stop_id", length = 20)
    private String toStopId;                    // null for INCOMPLETE

    @Column(name = "charge_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal chargeAmount;

    @Column(name = "company_id", nullable = false, length = 50)
    private String companyId;

    @Column(name = "bus_id", nullable = false, length = 50)
    private String busId;

    @Column(name = "masked_pan", nullable = false, length = 20)
    private String maskedPan;                   // ****5559 — never raw PAN

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TripStatus status;

    // --- Internal tracking fields ---

    @Column(name = "tap_on_event_id", nullable = false, unique = true)
    private Long tapOnEventId;

    @Column(name = "tap_off_event_id", unique = true)
    private Long tapOffEventId;                 // null for INCOMPLETE

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * SHA-256 hash of (pan + salt).
     * Used for matching TAP ON to TAP OFF.
     * Cannot be reversed to the original PAN.
     */
    @Column(name = "pan_hash", nullable = false, length = 64)
    private String panHash;
}
