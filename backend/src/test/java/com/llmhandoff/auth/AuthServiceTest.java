package com.llmhandoff.auth;

import com.llmhandoff.auth.dto.AuthResponse;
import com.llmhandoff.auth.dto.LoginRequest;
import com.llmhandoff.auth.dto.RegisterRequest;
import com.llmhandoff.auth.security.JwtTokenProvider;
import com.llmhandoff.user.AuthProvider;
import com.llmhandoff.user.User;
import com.llmhandoff.user.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, tokenProvider);
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("tony@example.com", "tony", "password123");

        when(userRepository.existsByEmail("tony@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        User savedUser = new User("tony@example.com", "tony", "hashedPassword", AuthProvider.LOCAL, null);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(tokenProvider.generateTokenFromUserId(any(), eq("tony@example.com"))).thenReturn("mock.jwt.token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.token());
        assertEquals("tony@example.com", response.user().email());
        assertEquals("tony", response.user().username());
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgumentException() {
        RegisterRequest request = new RegisterRequest("tony@example.com", "tony", "password123");

        when(userRepository.existsByEmail("tony@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("already in use"));
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("tony@example.com", "password123");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("mock.jwt.token");

        User user = new User("tony@example.com", "tony", "hashedPassword", AuthProvider.LOCAL, null);
        when(userRepository.findByEmail("tony@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.token());
        assertEquals("tony@example.com", response.user().email());
    }
}
