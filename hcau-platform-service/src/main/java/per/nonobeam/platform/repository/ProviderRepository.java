package per.nonobeam.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import per.nonobeam.platform.account.Provider;

public interface ProviderRepository extends JpaRepository<Provider, String> {}
