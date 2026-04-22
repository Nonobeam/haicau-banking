package per.nonobeam.common.account;

import jakarta.validation.constraints.NotBlank;

public record EnableCurrencyRequest(@NotBlank String ownerId, @NotBlank String currency) {}
