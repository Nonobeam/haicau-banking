package per.nonobeam.web.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.common.user.CreateUserRequest;
import per.nonobeam.common.user.UserResponse;
import per.nonobeam.web.common.account.User;
import per.nonobeam.web.common.account.UserType;
import per.nonobeam.web.repository.UserRepository;
import per.nonobeam.web.repository.UserTypeRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private static final String DEFAULT_USER_TYPE = "USER";

  private final UserRepository userRepository;
  private final UserTypeRepository userTypeRepository;

  @Transactional
  public UserResponse createUser(CreateUserRequest request) {
    UserType userType =
        userTypeRepository
            .findByName(DEFAULT_USER_TYPE)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "user_type '" + DEFAULT_USER_TYPE + "' not seeded in user_types table"));

    User saved =
        userRepository.save(User.builder().name(request.name()).userType(userType).build());

    log.info("User {} created (name={})", saved.getId(), saved.getName());

    return new UserResponse(
        saved.getId(), saved.getName(), userType.getName(), saved.getCreatedAt());
  }
}
