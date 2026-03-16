package per.nonobeam.web.model.deposit;

import jakarta.validation.constraints.NotBlank;

public record StubExpireRequest(
    @NotBlank(message = "Session ID must not be blank") String sessionId) {}
