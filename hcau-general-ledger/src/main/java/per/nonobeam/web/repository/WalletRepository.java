package per.nonobeam.web.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.Wallet;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

  Optional<Wallet> findByCustomerId(String customerId);
}
