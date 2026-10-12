package com.llmhandoff.auth;

import com.llmhandoff.auth.security.JwtTokenProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "LLMHandoffSecretKeyMustBeVeryLongForHS512AlgorithmSecurity20261008SecureKeyForAuth";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    void generateAndValidateToken_success() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";

        String token = tokenProvider.generateTokenFromUserId(userId, email);

        assertNotNull(token);
        assertTrue(tokenProvider.validateToken(token));
        assertEquals(userId, tokenProvider.getUserIdFromJWT(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(tokenProvider.validateToken("invalid.token.signature"));
    }
}
