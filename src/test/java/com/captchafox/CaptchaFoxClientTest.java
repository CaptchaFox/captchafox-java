package com.captchafox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** What the specification's cases leave out. */
@Timeout(10)
class CaptchaFoxClientTest {

    private static final String SECRET = "ok_11111111000000001111111100000000";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void fieldsArriveUnchangedWhateverTheirCharacters() throws Exception {
        try (LocalSiteverify server = LocalSiteverify.answering(200, "{\"success\":true}")) {
            String secret = "ok_a+b&c=d %ä";
            String token = "t+&=/?#% ä€";
            new CaptchaFoxClient(secret, TIMEOUT, server.endpoint()).verify(token, "sk_1", "2001:db8::1");

            Map<String, String> expected = Map.of("secret", secret, "response", token, "sitekey", "sk_1", "remoteIp", "2001:db8::1");
            assertEquals(expected, server.requests().get(0).form);
        }
    }

    @Test
    void aNullTokenIsRejectedWithoutARequest() throws Exception {
        try (LocalSiteverify server = LocalSiteverify.answering(200, "{\"success\":true}")) {
            Verification verification = new CaptchaFoxClient(SECRET, TIMEOUT, server.endpoint()).verify(null);

            Verification.Rejected rejected = assertInstanceOf(Verification.Rejected.class, verification);
            assertEquals(List.of("missing-input-response"), rejected.errorCodes());
            assertEquals(List.of(), server.requests());
        }
    }

    @Test
    void aRepeatedSuccessCannotBeEvaluated() {
        assertThrows(VerificationUnavailableException.class, () -> CaptchaFoxClient.evaluate("{\"success\":false,\"success\":true}"));
    }

    @Test
    void aDeeplyNestedBodyCannotBeEvaluated() {
        String body = "{\"success\":true,\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}";

        assertThrows(VerificationUnavailableException.class, () -> CaptchaFoxClient.evaluate(body));
    }

    @Test
    void theDefaultTimeoutIsFiveSeconds() {
        assertEquals(Duration.ofSeconds(5), new CaptchaFoxClient(SECRET).timeout());
    }

    @Test
    void theTimeoutEndsAnAnswerThatNeverCompletesAndClosesTheConnection() throws Exception {
        try (LocalSiteverify server = LocalSiteverify.trickling(200, "{\"success\":true")) {
            CaptchaFoxClient client = new CaptchaFoxClient(SECRET, Duration.ofSeconds(1), server.endpoint());
            long start = System.nanoTime();

            assertThrows(VerificationUnavailableException.class, () -> client.verify("token"));
            long elapsed = System.nanoTime() - start;
            assertTrue(elapsed < Duration.ofSeconds(3).toNanos(), "the verification took " + elapsed / 1_000_000 + " ms");

            // The server notices the closed connection when it writes its next space.
            long limit = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (server.brokenAtNanos() == 0 && System.nanoTime() < limit) {
                Thread.sleep(50);
            }
            assertNotEquals(0, server.brokenAtNanos(), "the connection is still open");
        }
    }

    @Test
    void theSecretKeyIsNotInToString() {
        assertFalse(new CaptchaFoxClient(SECRET).toString().contains(SECRET));
    }

    @Test
    void anEmptySecretKeyOrNoPositiveTimeoutIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CaptchaFoxClient(""));
        assertThrows(NullPointerException.class, () -> new CaptchaFoxClient(null));
        assertThrows(IllegalArgumentException.class, () -> new CaptchaFoxClient(SECRET, Duration.ZERO));
    }
}
