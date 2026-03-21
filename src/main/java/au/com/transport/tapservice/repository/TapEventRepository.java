package au.com.transport.tapservice.repository;

import au.com.transport.tapservice.entity.TapEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TapEventRepository extends JpaRepository<TapEvent, Long> {

    boolean existsByOriginalIdAndTappedAtAndPanHash(Long originalId, java.time.LocalDateTime tappedAt, String panHash);

    java.util.Optional<TapEvent> findByOriginalIdAndTappedAtAndPanHash(Long originalId, java.time.LocalDateTime tappedAt, String panHash);
}
