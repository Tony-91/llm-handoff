package com.llmhandoff.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llmhandoff.auth.dto.AuthResponse;
import com.llmhandoff.auth.dto.LoginRequest;
import com.llmhandoff.auth.dto.RegisterRequest;
import com.llmhandoff.auth.dto.UserResponse;
import com.llmhandoff.error.GlobalExceptionHandler;
import com.llmhandoff.user.AuthProvider;
import com.llmhandoff.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("tony@example.com", "tony", "password123");
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "tony@example.com", "tony", AuthProvider.LOCAL);
        AuthResponse response = new AuthResponse("mock.token", userResponse);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("mock.token"))
                .andExpect(jsonPath("$.user.email").value("tony@example.com"))
                .andExpect(jsonPath("$.user.username").value("tony"));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("invalid-email", "tony", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest("tony@example.com", "password123");
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "tony@example.com", "tony", AuthProvider.LOCAL);
        AuthResponse response = new AuthResponse("mock.token", userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.token"))
                .andExpect(jsonPath("$.user.email").value("tony@example.com"));
    }
}
