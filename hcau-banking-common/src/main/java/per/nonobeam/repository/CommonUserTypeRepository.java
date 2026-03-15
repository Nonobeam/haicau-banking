package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.UserType;

@Repository
public interface CommonUserTypeRepository extends JpaRepository<UserType, String> {}
