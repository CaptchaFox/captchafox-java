package com.captchafox.integrator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.captchafox.CaptchaFoxClient;
import com.captchafox.Verification;
import com.captchafox.VerificationUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Uses the library from outside its package, as an integrator does: a member that stops being public fails here. */
class PublicInterfaceTest {

    private static final String SECRET = "ok_11111111000000001111111100000000";
    private static final String SITEKEY = "sk_11111111000000001111111100000000";

    // An empty token is rejected without a request, so no network is needed.
    @Test
    void everyPublicMemberIsReachable() throws VerificationUnavailableException {
        CaptchaFoxClient client = new CaptchaFoxClient(SECRET);
        CaptchaFoxClient withTimeout = new CaptchaFoxClient(SECRET, Duration.ofSeconds(3));

        assertEquals("rejected: missing-input-response", describe(client.verify("")));
        assertEquals("rejected: missing-input-response", describe(withTimeout.verify("", SITEKEY, "203.0.113.7")));
    }

    private static String describe(Verification verification) {
        if (verification.isPassed()) {
            Verification.Passed passed = (Verification.Passed) verification;
            return "passed: " + passed.challengeTs().orElse("") + " " + passed.hostname().orElse("");
        } else {
            Verification.Rejected rejected = (Verification.Rejected) verification;
            return "rejected: " + String.join(",", rejected.errorCodes());
        }
    }
}
