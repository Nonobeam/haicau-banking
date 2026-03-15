package per.nonobeam.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.ledger.TransactionType;

@Repository
public interface CommonTransactionTypeRepository extends JpaRepository<TransactionType, String> {
  Optional<TransactionType> findByName(String type);
}
