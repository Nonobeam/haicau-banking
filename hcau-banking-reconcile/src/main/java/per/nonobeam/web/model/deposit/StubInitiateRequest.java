package per.nonobeam.web.model.deposit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StubInitiateRequest(
    @NotNull(message = "Amount must not be null") Long amount,
    @NotBlank(message = "Currency must not be blank") String currency) {}
