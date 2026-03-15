package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.BucketType;

@Repository
public interface CommonBucketTypeRepository extends JpaRepository<BucketType, String> {
  Optional<BucketType> findByName(String name);
}
