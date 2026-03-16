package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.config.ExternalProvider;

@Repository
public interface CommonExternalProviderRepository extends JpaRepository<ExternalProvider, String> {}
