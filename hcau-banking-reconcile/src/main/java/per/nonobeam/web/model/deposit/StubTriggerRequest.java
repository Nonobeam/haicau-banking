package per.nonobeam.web.model.deposit;

import jakarta.validation.constraints.NotBlank;

public record StubTriggerRequest(
    @NotBlank(message = "Session ID must not be blank") String sessionId,
    @NotBlank(message = "Outcome must not be blank") String outcome) {}
