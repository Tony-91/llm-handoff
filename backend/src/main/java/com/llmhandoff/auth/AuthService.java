package com.llmhandoff.auth;

import com.llmhandoff.auth.dto.AuthResponse;
import com.llmhandoff.auth.dto.LoginRequest;
import com.llmhandoff.auth.dto.RegisterRequest;
import com.llmhandoff.auth.dto.UserResponse;
import com.llmhandoff.auth.security.JwtTokenProvider;
import com.llmhandoff.user.AuthProvider;
import com.llmhandoff.user.User;
import com.llmhandoff.user.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already in use: " + normalizedEmail);
        }

        User user = new User(
                normalizedEmail,
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                AuthProvider.LOCAL,
                null
        );

        User savedUser = userRepository.save(user);
        String token = tokenProvider.generateTokenFromUserId(savedUser.getId(), savedUser.getEmail());

        return new AuthResponse(token, UserResponse.fromEntity(savedUser));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        String token = tokenProvider.generateToken(authentication);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + normalizedEmail));

        return new AuthResponse(token, UserResponse.fromEntity(user));
    }
}
