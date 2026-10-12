package com.llmhandoff.auth.dto;

import com.llmhandoff.user.AuthProvider;
import com.llmhandoff.user.User;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        AuthProvider provider
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getProvider()
        );
    }
}
