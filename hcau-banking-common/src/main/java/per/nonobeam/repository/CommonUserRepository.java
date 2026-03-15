package per.nonobeam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.common.account.User;

@Repository
public interface CommonUserRepository extends JpaRepository<User, String> {}
