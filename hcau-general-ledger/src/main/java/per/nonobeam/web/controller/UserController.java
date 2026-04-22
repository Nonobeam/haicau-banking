package per.nonobeam.web.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.common.user.CreateUserRequest;
import per.nonobeam.common.user.UserResponse;
import per.nonobeam.web.service.UserService;

@RestController
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping("/internal/v1/users")
  public UserResponse createUser(@RequestBody CreateUserRequest request) {
    return userService.createUser(request);
  }
}
