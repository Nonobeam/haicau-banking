package per.nonobeam.web.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.account.UserType;

@Repository
public interface UserTypeRepository extends JpaRepository<UserType, String> {
  Optional<UserType> findByName(String name);
}
