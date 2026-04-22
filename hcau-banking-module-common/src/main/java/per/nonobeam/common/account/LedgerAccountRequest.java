package per.nonobeam.common.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LedgerAccountRequest(
    @NotBlank String ownerId, @NotEmpty @Size(max = 100) List<@NotBlank String> currencies) {}
