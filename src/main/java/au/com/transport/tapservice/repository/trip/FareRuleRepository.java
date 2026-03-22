package au.com.transport.tapservice.repository.trip;

import au.com.transport.tapservice.entity.trip.FareRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FareRuleRepository extends JpaRepository<FareRule, Long> {

    Optional<FareRule> findByFromStopIdAndToStopId(String fromStopId, String toStopId);

    /**
     * Max fare from a given origin stop.
     * Used for INCOMPLETE trips where destination is unknown.
     */
    @Query("""
            SELECT f FROM FareRule f
            WHERE f.fromStopId = :fromStopId
            ORDER BY f.fareAmount DESC
            LIMIT 1
            """)
    Optional<FareRule> findMaxFareFromStop(@Param("fromStopId") String fromStopId);

}
