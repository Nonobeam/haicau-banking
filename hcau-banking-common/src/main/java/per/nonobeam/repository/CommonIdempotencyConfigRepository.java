package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.config.IdempotencyConfig;

@Repository
public interface CommonIdempotencyConfigRepository
    extends JpaRepository<IdempotencyConfig, String> {}
