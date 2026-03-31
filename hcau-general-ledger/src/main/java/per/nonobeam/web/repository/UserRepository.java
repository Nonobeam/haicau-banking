package per.nonobeam.web.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {}
