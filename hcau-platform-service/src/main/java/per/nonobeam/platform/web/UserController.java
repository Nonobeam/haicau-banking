package per.nonobeam.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.common.channel.ChannelRequest;
import per.nonobeam.common.user.CreateUserRequest;
import per.nonobeam.common.user.UserResponse;
import per.nonobeam.internal.channel.HttpCommunicationChannel;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private static final String DESTINATION = "general-ledger/internal/v1/users";

  private final HttpCommunicationChannel channel;

  @PostMapping
  public UserResponse createUser(@RequestBody CreateUserRequest request) {
    return channel.push(DESTINATION, ChannelRequest.of(request), UserResponse.class).body();
  }
}
