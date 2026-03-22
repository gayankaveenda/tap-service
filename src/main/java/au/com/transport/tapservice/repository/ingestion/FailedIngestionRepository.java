package au.com.transport.tapservice.repository.ingestion;

import au.com.transport.tapservice.entity.ingestion.FailedIngestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedIngestionRepository extends JpaRepository<FailedIngestion, Long> {

}
