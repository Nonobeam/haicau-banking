package per.nonobeam.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.config.IdempotencyKey;

@Repository
public interface CommonIdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
  Optional<IdempotencyKey> findByIdempotencyKeyAndExpiresAtAfter(
      String idempotencyKey, OffsetDateTime now);
}
