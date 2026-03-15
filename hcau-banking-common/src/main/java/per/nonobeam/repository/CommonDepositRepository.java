package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.ledger.Transaction;

@Repository
public interface CommonDepositRepository extends JpaRepository<Transaction, String> {}
