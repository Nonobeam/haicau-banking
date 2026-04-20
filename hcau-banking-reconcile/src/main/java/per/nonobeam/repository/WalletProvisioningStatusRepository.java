package per.nonobeam.repository;

import org.springframework.stereotype.Repository;

@Repository
public interface WalletProvisioningStatusRepository
    extends CommonWalletProvisioningStatusRepository {

  default boolean isProvisioned(String userId) {
    return existsById(userId);
  }
}
