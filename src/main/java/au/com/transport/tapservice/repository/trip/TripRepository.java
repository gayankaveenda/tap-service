package au.com.transport.tapservice.repository.trip;

import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByOrderByStartedAsc();

    boolean existsByTapOnEventId(Long tapOnEventId);

    List<Trip> findByStatusOrderByStartedAsc(TripStatus status);

    List<Trip> findByStartedBetweenOrderByStartedAsc(
            LocalDateTime startedFrom,
            LocalDateTime startedTo
    );

    List<Trip> findByStatusAndStartedBetweenOrderByStartedAsc(
            TripStatus status,
            LocalDateTime startedFrom,
            LocalDateTime startedTo
    );
}
