package per.nonobeam.platform.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import per.nonobeam.platform.account.UserContact;

public interface UserContactRepository extends JpaRepository<UserContact, String> {
  Optional<UserContact> findByUserId(String userId);
}
