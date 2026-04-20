package per.nonobeam.web.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CommonQueryService {

  private final UserRepository userRepository;

  public User getUser(String id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.USER_NOT_FOUND, id));
  }
}
