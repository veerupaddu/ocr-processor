package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.config.JwtUtil;
import ai.medhaleak.ocrprocessor.dto.ChangePasswordRequest;
import ai.medhaleak.ocrprocessor.dto.LoginRequest;
import ai.medhaleak.ocrprocessor.dto.RegisterRequest;
import ai.medhaleak.ocrprocessor.exception.*;
import ai.medhaleak.ocrprocessor.model.User;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AppProperties props;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, AppProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.props = props;
    }

    public void register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (!request.password().equals(request.confirmPassword())) {
            throw new BadCredentialsException("Passwords do not match");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateUsernameException(username);
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= props.security().maxLoginAttempts()) {
                user.setLockedUntil(Instant.now().plusSeconds(
                        props.security().lockoutDurationMinutes() * 60L));
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid username or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadCredentialsException("Passwords do not match");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("New password must differ from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
