package per.nonobeam.web.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.BucketType;

@Repository
public interface BucketTypeRepository extends JpaRepository<BucketType, UUID> {
  Optional<BucketType> findByName(String name);
}
