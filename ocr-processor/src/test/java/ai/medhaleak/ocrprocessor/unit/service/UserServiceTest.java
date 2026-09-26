package ai.medhaleak.ocrprocessor.unit.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.config.JwtUtil;
import ai.medhaleak.ocrprocessor.dto.ChangePasswordRequest;
import ai.medhaleak.ocrprocessor.dto.LoginRequest;
import ai.medhaleak.ocrprocessor.dto.RegisterRequest;
import ai.medhaleak.ocrprocessor.exception.AccountLockedException;
import ai.medhaleak.ocrprocessor.exception.DuplicateEmailException;
import ai.medhaleak.ocrprocessor.exception.DuplicateUsernameException;
import ai.medhaleak.ocrprocessor.model.User;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import ai.medhaleak.ocrprocessor.service.UserService;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private AppProperties props;
    private UserService userService;

    @BeforeEach
    void setUp() {
        props = new AppProperties(
                new AppProperties.Jwt("secret-key-that-is-long-enough-32ch", 8),
                new AppProperties.Ocr(20_971_520L, List.of("application/pdf", "image/png")),
                new AppProperties.Llm(10000, "Summarise: {extractedText}"),
                new AppProperties.Security(5, 15)
        );
        userService = new UserService(userRepository, passwordEncoder, new StubJwt(props), props);
    }

    // --- register ---

    @Test
    void register_success() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");

        userService.register(new RegisterRequest("alice", "alice@example.com", "password1", "password1"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        Evidence.record("username=alice email=alice@example.com password=password1", "saved",
                "username=" + saved.getValue().getUsername() + " passwordHash=" + saved.getValue().getPasswordHash());
    }

    @Test
    void register_throwsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(true);

        Throwable error = catchThrowable(() -> userService.register(
                new RegisterRequest("alice", "a@b.com", "password1", "password1")));
        assertThat(error).isInstanceOf(DuplicateUsernameException.class);
        Evidence.record("username=alice email=a@b.com", error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void register_throwsDuplicateEmail() {
        when(userRepository.existsByUsernameIgnoreCase("alice")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        Throwable error = catchThrowable(() -> userService.register(
                new RegisterRequest("alice", "alice@example.com", "password1", "password1")));
        assertThat(error).isInstanceOf(DuplicateEmailException.class);
        Evidence.record("username=alice email=alice@example.com", error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void register_throwsOnPasswordMismatch() {
        Throwable error = catchThrowable(() -> userService.register(
                new RegisterRequest("alice", "a@b.com", "password1", "different")));
        assertThat(error).isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Passwords do not match");
        verify(userRepository, never()).save(any());
        Evidence.record("username=alice password=password1 confirm=different", error.getClass().getSimpleName(), error.getMessage());
    }

    // --- login ---

    @Test
    void login_success_returnsToken() {
        User user = buildUser("alice", "hashed", 0, null);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);

        String token = userService.login(new LoginRequest("alice", "password1"));

        assertThat(token).isEqualTo("token123");
        assertThat(user.getFailedLoginAttempts()).isZero();
        Evidence.record("username=alice password=password1 storedAttempts=0", "authenticated",
                "token=" + token + " failedLoginAttempts=" + user.getFailedLoginAttempts());
    }

    @Test
    void login_badPassword_incrementsAttempts() {
        User user = buildUser("alice", "hashed", 2, null);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Throwable error = catchThrowable(() -> userService.login(new LoginRequest("alice", "wrong")));
        assertThat(error).isInstanceOf(BadCredentialsException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(3);
        Evidence.record("username=alice password=wrong storedAttempts=2", error.getClass().getSimpleName(),
                error.getMessage() + " failedLoginAttempts=" + user.getFailedLoginAttempts());
    }

    @Test
    void login_locksAccountAfterFiveAttempts() {
        User user = buildUser("alice", "hashed", 4, null);
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Throwable error = catchThrowable(() -> userService.login(new LoginRequest("alice", "wrong")));
        assertThat(error).isInstanceOf(BadCredentialsException.class);
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        Evidence.record("username=alice password=wrong storedAttempts=4 maxAttempts=5", error.getClass().getSimpleName(),
                error.getMessage() + " failedLoginAttempts=" + user.getFailedLoginAttempts() + " lockedUntil=" + user.getLockedUntil());
    }

    @Test
    void login_throwsAccountLockedWhenLockActive() {
        User user = buildUser("alice", "hashed", 5, Instant.now().plusSeconds(600));
        when(userRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));

        Throwable error = catchThrowable(() -> userService.login(new LoginRequest("alice", "any")));
        assertThat(error).isInstanceOf(AccountLockedException.class);
        Evidence.record("username=alice password=any lockedUntil=" + user.getLockedUntil(),
                error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void login_unknownUser_throwsBadCredentials() {
        when(userRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        Throwable error = catchThrowable(() -> userService.login(new LoginRequest("nobody", "x")));
        assertThat(error).isInstanceOf(BadCredentialsException.class);
        Evidence.record("username=nobody password=x", error.getClass().getSimpleName(), error.getMessage());
    }

    // --- changePassword ---

    @Test
    void changePassword_success() {
        UUID id = UUID.randomUUID();
        User user = buildUser("alice", "hashed", 0, null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("newpass1", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("newpass1")).thenReturn("newhashed");

        userService.changePassword(id, new ChangePasswordRequest("old", "newpass1", "newpass1"));

        assertThat(user.getPasswordHash()).isEqualTo("newhashed");
        Evidence.record("userId=" + id + " current=old new=newpass1", "password replaced",
                "passwordHash=" + user.getPasswordHash());
    }

    @Test
    void changePassword_wrongCurrent_throws() {
        UUID id = UUID.randomUUID();
        User user = buildUser("alice", "hashed", 0, null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        Throwable error = catchThrowable(() -> userService.changePassword(id,
                new ChangePasswordRequest("wrong", "newpass1", "newpass1")));
        assertThat(error).isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Current password is incorrect");
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        Evidence.record("userId=" + id + " current=wrong new=newpass1", error.getClass().getSimpleName(),
                error.getMessage() + " passwordHash=" + user.getPasswordHash());
    }

    @Test
    void changePassword_sameAsOld_throws() {
        UUID id = UUID.randomUUID();
        User user = buildUser("alice", "hashed", 0, null);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true); // new == old

        Throwable error = catchThrowable(() -> userService.changePassword(id,
                new ChangePasswordRequest("old", "old", "old")));
        assertThat(error).isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("New password must differ");
        Evidence.record("userId=" + id + " current=old new=old", error.getClass().getSimpleName(), error.getMessage());
    }

    // helpers

    /** Real signer. Mockito cannot mock JwtUtil on newer JDKs. */
    private static final class StubJwt extends JwtUtil {
        private StubJwt(AppProperties props) {
            super(props);
        }

        @Override
        public String generateToken(UUID userId, String username) {
            return "token123";
        }
    }

    private User buildUser(String username, String hash, int attempts, Instant lockedUntil) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPasswordHash(hash);
        u.setFailedLoginAttempts(attempts);
        u.setLockedUntil(lockedUntil);
        return u;
    }
}
