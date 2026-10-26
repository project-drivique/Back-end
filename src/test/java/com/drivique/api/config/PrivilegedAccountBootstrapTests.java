package com.drivique.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PrivilegedAccountBootstrapTests {

    private static final String STRONG = "una-clave-larga-y-aleatoria-9X";
    private static final String PUBLISHED_HASH = "$2a$12$iIrC/bchcMQVAN7jkJSuTOGvGKtorg5NKnP3qRcXpsdEHM5jP02BS";
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private User userWith(String hash) {
        User user = new User("Super", "Admin", "admin@drivique.com", hash);
        when(users.findByEmailIgnoreCase("admin@drivique.com")).thenReturn(Optional.of(user));
        return user;
    }

    private PrivilegedAccountBootstrap bootstrap(boolean requireSecrets) {
        return new PrivilegedAccountBootstrap(users, encoder, "", "", requireSecrets);
    }

    @Test
    void lockedAccountReceivesPasswordFromEnvironment() {
        User user = userWith(PrivilegedAccountBootstrap.LOCKED_MARKER);
        bootstrap(true).apply("admin@drivique.com", STRONG, "X");

        assertThat(encoder.matches(STRONG, user.getPasswordHash())).isTrue();
        verify(users).save(user);
    }

    @Test
    void accountStillUsingThePublishedPasswordIsReplaced() {
        User user = userWith(encoder.encode("DriviqueAdmin2026!"));
        bootstrap(true).apply("admin@drivique.com", STRONG, "X");

        assertThat(encoder.matches("DriviqueAdmin2026!", user.getPasswordHash())).isFalse();
        assertThat(encoder.matches(STRONG, user.getPasswordHash())).isTrue();
    }

    @Test
    void protectedEnvironmentWithoutPasswordKeepsAccountLocked() {
        User user = userWith(PUBLISHED_HASH);
        bootstrap(true).apply("admin@drivique.com", "", "X");

        assertThat(user.getPasswordHash()).isEqualTo(PrivilegedAccountBootstrap.LOCKED_MARKER);
    }

    @Test
    void publishedPasswordIsRejectedAsConfiguredValue() {
        User user = userWith(PrivilegedAccountBootstrap.LOCKED_MARKER);
        bootstrap(true).apply("admin@drivique.com", "DriviqueAdmin2026!", "X");

        assertThat(user.getPasswordHash()).isEqualTo(PrivilegedAccountBootstrap.LOCKED_MARKER);
    }

    @Test
    void passwordChangedByThePersonIsNeverOverwritten() {
        String mine = encoder.encode("mi-clave-personal-123");
        User user = userWith(mine);
        bootstrap(true).apply("admin@drivique.com", STRONG, "X");

        assertThat(user.getPasswordHash()).isEqualTo(mine);
        verify(users, never()).save(any(User.class));
    }

    @Test
    void developmentKeepsAnExistingLocalPasswordButNeverInProtectedEnvironments() {
        User user = userWith(PUBLISHED_HASH);
        bootstrap(false).apply("admin@drivique.com", "", "X");

        assertThat(user.getPasswordHash()).isEqualTo(PUBLISHED_HASH);
        verify(users, never()).save(any(User.class));
    }

    @Test
    void developmentGeneratesATemporaryPasswordInsteadOfAKnownOne() {
        User user = userWith(PrivilegedAccountBootstrap.LOCKED_MARKER);
        bootstrap(false).apply("admin@drivique.com", "", "X");

        assertThat(user.getPasswordHash()).startsWith("$2a$");
        assertThat(encoder.matches("DriviqueAdmin2026!", user.getPasswordHash())).isFalse();
    }
}
