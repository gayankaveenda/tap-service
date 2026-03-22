package au.com.transport.tapservice.repository.ingestion;

import au.com.transport.tapservice.entity.ingestion.TapEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TapEventRepository extends JpaRepository<TapEvent, Long> {

    boolean existsByOriginalIdAndTappedAtAndPanHash(Long originalId, java.time.LocalDateTime tappedAt, String panHash);

    @Query("""
            SELECT t FROM TapEvent t
            WHERE t.status = 'PENDING'
            ORDER BY t.tappedAt DESC
            """)
    List<TapEvent> findPendingBatch(org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT t FROM TapEvent t
            WHERE t.panHash = :panHash
            AND t.busId = :busId
            AND t.tapType = 'ON'
            AND t.status = 'PENDING'
            ORDER BY t.tappedAt DESC
            """)
    List<TapEvent> findPendingTapOn(@Param("panHash") String panHash,
                                    @Param("busId") String busId);


    @Query("""
            SELECT t FROM TapEvent t
            WHERE t.status = 'PENDING'
            AND t.tapType = 'ON'
            AND t.createdAt < :cutoffTime
            ORDER BY t.tappedAt ASC
            """)
    List<TapEvent> findOrphanCandidates(
            @Param("cutoffTime") java.time.LocalDateTime cutoffTime,
            org.springframework.data.domain.Pageable pageable
    );

}
