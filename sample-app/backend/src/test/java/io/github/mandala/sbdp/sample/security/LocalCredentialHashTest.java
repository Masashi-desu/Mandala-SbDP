package io.github.mandala.sbdp.sample.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalCredentialHashTest {
    private static final Pattern USER_ROW = Pattern.compile("\\('([^']+)', '([^']+)'", Pattern.MULTILINE);

    @Test
    void flywaySeedContainsOnlyMatchingCostTwelveBcryptHashes() throws IOException {
        String migration;
        try (var stream = new ClassPathResource(
                "db/migration/V2__seed_local_users_and_sample_data.sql").getInputStream()) {
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher rows = USER_ROW.matcher(migration);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        int verified = 0;
        while (rows.find()) {
            String username = rows.group(1);
            String hash = rows.group(2);
            assertThat(hash).startsWith("$2a$12$");
            if (username.equals("local-admin")) {
                assertThat(encoder.matches("mandala-admin", hash)).isTrue();
                verified++;
            } else if (username.equals("local-user")) {
                assertThat(encoder.matches("mandala-user", hash)).isTrue();
                verified++;
            }
        }
        assertThat(verified).isEqualTo(2);
    }
}
