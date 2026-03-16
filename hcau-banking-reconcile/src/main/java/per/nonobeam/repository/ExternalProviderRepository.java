package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.config.ExternalProvider;

@Repository
public interface ExternalProviderRepository extends CommonExternalProviderRepository {
  Optional<ExternalProvider> findByName(String name);
}
