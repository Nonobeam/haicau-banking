package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.model.deposit.DepositMetadata;

@Repository
public interface DepositMetadataRepository extends JpaRepository<DepositMetadata, String> {}
