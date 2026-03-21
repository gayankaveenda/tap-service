package au.com.transport.tapservice.repository;

import au.com.transport.tapservice.entity.FailedIngestionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedIngestionRepository extends JpaRepository<FailedIngestionRecord, Long> {

}
