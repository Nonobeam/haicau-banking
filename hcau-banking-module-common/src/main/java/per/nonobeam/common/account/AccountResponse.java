package per.nonobeam.common.account;

import java.time.OffsetDateTime;

public record AccountResponse(
    String id,
    String ownerId,
    String coaPath,
    String accountType,
    String state,
    String walletId,
    AccountStatus status,
    OffsetDateTime createdAt) {}
