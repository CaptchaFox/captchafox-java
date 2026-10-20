package com.captchafox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Verifies the public test keys against CaptchaFox's siteverify; needs the network. */
@Tag("live")
class LiveSiteverifyTest {

    private static final String PASSING_SECRET = "ok_11111111000000001111111100000000";
    private static final String FAILING_SECRET = "ok_FFFFFFFF00000000FFFFFFFF00000000";
    private static final String SITEKEY = "sk_11111111000000001111111100000000";
    private static final String TOKEN = "11111111000000001111111100000000";

    @Test
    void theTestKeyPasses() throws Exception {
        assertInstanceOf(Verification.Passed.class, new CaptchaFoxClient(PASSING_SECRET).verify(TOKEN));
    }

    @Test
    void theTestKeyPassesWithSitekeyAndRemoteIp() throws Exception {
        assertInstanceOf(Verification.Passed.class, new CaptchaFoxClient(PASSING_SECRET).verify(TOKEN, SITEKEY, "203.0.113.7"));
    }

    @Test
    void theFailingTestKeyIsRejected() throws Exception {
        Verification verification = new CaptchaFoxClient(FAILING_SECRET).verify(TOKEN);

        assertEquals(List.of("timeout-or-duplicate"), assertInstanceOf(Verification.Rejected.class, verification).errorCodes());
    }
}
