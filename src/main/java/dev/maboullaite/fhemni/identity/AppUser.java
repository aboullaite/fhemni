package dev.maboullaite.fhemni.identity;

import java.time.Instant;
import java.util.UUID;

public record AppUser(
        UUID id,
        String displayName,
        String email,
        String avatarUrl,
        UserRole role,
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt) {
}
