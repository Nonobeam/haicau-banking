package per.nonobeam.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.platform.account.UserContact;
import per.nonobeam.platform.repository.UserContactRepository;
import per.nonobeam.platform.web.dto.UserContactRequest;
import per.nonobeam.platform.web.dto.UserContactResponse;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class UserContactController {

  private final UserContactRepository userContactRepository;

  @GetMapping("/{userId}/contact")
  public ResponseEntity<UserContactResponse> getContact(@PathVariable String userId) {
    return userContactRepository
        .findByUserId(userId)
        .map(
            c ->
                ResponseEntity.ok(
                    new UserContactResponse(c.getUserId(), c.getEmail(), c.getTelegramChatId())))
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{userId}/contact")
  public ResponseEntity<UserContactResponse> upsertContact(
      @PathVariable String userId, @RequestBody UserContactRequest request) {
    UserContact contact =
        userContactRepository
            .findByUserId(userId)
            .orElse(
                UserContact.builder()
                    .userId(userId)
                    .createdAt(java.time.OffsetDateTime.now())
                    .build());
    contact.setEmail(request.email());
    contact.setTelegramChatId(request.telegramChatId());
    contact.setUpdatedAt(java.time.OffsetDateTime.now());
    userContactRepository.save(contact);
    return ResponseEntity.ok(
        new UserContactResponse(userId, contact.getEmail(), contact.getTelegramChatId()));
  }
}
