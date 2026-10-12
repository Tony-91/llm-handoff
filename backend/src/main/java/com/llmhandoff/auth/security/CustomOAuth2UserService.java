package com.llmhandoff.auth.security;

import com.llmhandoff.user.AuthProvider;
import com.llmhandoff.user.User;
import com.llmhandoff.user.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String providerId;
        String email;
        String name;

        if (provider == AuthProvider.GITHUB) {
            Object idObj = attributes.get("id");
            providerId = idObj != null ? String.valueOf(idObj) : null;
            email = (String) attributes.get("email");
            if (email == null) {
                // If email is private on GitHub, generate a fallback handle
                email = attributes.get("login") + "@github.user";
            }
            name = (String) attributes.get("name");
            if (name == null) {
                name = (String) attributes.get("login");
            }
        } else if (provider == AuthProvider.GOOGLE) {
            providerId = (String) attributes.get("sub");
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        } else {
            providerId = (String) attributes.get("id");
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        }

        if (name == null || name.isBlank()) {
            name = email != null ? email.split("@")[0] : "OAuth User";
        }

        User user = processOAuthUser(provider, providerId, email, name);
        return UserPrincipal.create(user, attributes);
    }

    private User processOAuthUser(AuthProvider provider, String providerId, String email, String name) {
        Optional<User> userOptional = userRepository.findByProviderAndProviderId(provider, providerId);
        if (userOptional.isPresent()) {
            User existingUser = userOptional.get();
            existingUser.setUsername(name);
            return userRepository.save(existingUser);
        }

        if (email != null) {
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                User existingUser = userByEmail.get();
                existingUser.setProvider(provider);
                existingUser.setProviderId(providerId);
                return userRepository.save(existingUser);
            }
        }

        User newUser = new User(email, name, null, provider, providerId);
        return userRepository.save(newUser);
    }
}
