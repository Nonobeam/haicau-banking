package per.nonobeam.web.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.DomainType;

@Repository
public interface DomainTypeRepository extends JpaRepository<DomainType, UUID> {
  Optional<DomainType> findByName(String name);
}
