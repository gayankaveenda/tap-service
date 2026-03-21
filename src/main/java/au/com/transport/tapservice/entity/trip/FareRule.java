package au.com.transport.tapservice.entity.trip;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "fare_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fare_from_to",
                columnNames = {"from_stop_id", "to_stop_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FareRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fare_rule_seq")
    @SequenceGenerator(name = "fare_rule_seq", sequenceName = "fare_rule_seq", allocationSize = 10)
    private Long id;

    @Column(name = "from_stop_id", nullable = false, length = 20)
    private String fromStopId;

    @Column(name = "to_stop_id", nullable = false, length = 20)
    private String toStopId;

    @Column(name = "fare_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal fareAmount;
}
