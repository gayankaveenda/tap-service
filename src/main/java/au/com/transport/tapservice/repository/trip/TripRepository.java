package au.com.transport.tapservice.repository.trip;

import au.com.transport.tapservice.entity.trip.Trip;
import au.com.transport.tapservice.entity.trip.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    List<Trip> findAllByOrderByStartedAsc();

    List<Trip> findByStatus(TripStatus status);

    boolean existsByTapOnEventId(Long tapOnEventId);
}
