package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.DomainType;

@Repository
public interface CommonDomainTypeRepository extends JpaRepository<DomainType, String> {
  Optional<DomainType> findByName(String name);
}
