package per.nonobeam.common.user;

import java.time.OffsetDateTime;

public record UserResponse(String id, String name, String userType, OffsetDateTime createdAt) {}
