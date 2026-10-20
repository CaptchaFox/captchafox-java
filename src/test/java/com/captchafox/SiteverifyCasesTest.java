package com.captchafox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Runs every case of the siteverify specification against a local siteverify. */
class SiteverifyCasesTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    // Short, so that the cases of a siteverify that stops answering end quickly.
    private static final Duration SILENCE_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration CASE_BOUND = Duration.ofSeconds(10);

    @TestFactory
    Stream<DynamicTest> everyCase() throws IOException {
        Stream<JsonNode> cases = StreamSupport.stream(specification().get("cases").spliterator(), false);
        return cases.map(testCase -> {
            String name = testCase.get("name").asText();
            return DynamicTest.dynamicTest(name, () -> {
                // Maven's console names a dynamic test by its index only. The bound fails a case whose timeout does
                // not take effect instead of letting it hang.
                try {
                    assertTimeoutPreemptively(CASE_BOUND, () -> run(testCase));
                } catch (AssertionError e) {
                    throw new AssertionError(name + ": " + e.getMessage(), e);
                }
            });
        });
    }

    @Test
    void theEndpointIsTheOneOfTheSpecification() throws IOException {
        assertEquals(URI.create(specification().get("endpoint").asText()), CaptchaFoxClient.ENDPOINT);
    }

    private static void run(JsonNode testCase) throws Exception {
        JsonNode input = testCase.get("input");
        JsonNode answer = testCase.get("answer");
        String secret = input.get("secret").asText();

        LocalSiteverify server = null;
        URI endpoint;
        Duration timeout = TIMEOUT;
        if (answer.isNull()) {
            server = LocalSiteverify.answering(500, "");
            endpoint = server.endpoint();
        } else if (!answer.has("error")) {
            server = LocalSiteverify.answering(answer.get("status").asInt(), answer.get("body").asText());
            endpoint = server.endpoint();
        } else {
            String error = answer.get("error").asText();
            switch (error) {
                case "timeout":
                    server = LocalSiteverify.silent();
                    endpoint = server.endpoint();
                    timeout = SILENCE_TIMEOUT;
                    break;
                case "stall":
                    server = LocalSiteverify.stalling(answer.get("status").asInt(), answer.get("body").asText());
                    endpoint = server.endpoint();
                    timeout = SILENCE_TIMEOUT;
                    break;
                case "connection":
                    endpoint = LocalSiteverify.refusingEndpoint();
                    break;
                default:
                    throw new IllegalArgumentException("unknown answer error " + error);
            }
        }

        try {
            CaptchaFoxClient client = new CaptchaFoxClient(secret, timeout, endpoint);
            String token = input.get("response").asText();
            String sitekey = textOrNull(input, "sitekey");
            String remoteIp = textOrNull(input, "remoteIp");

            expectOutcome(testCase.get("expect"), secret, () -> client.verify(token, sitekey, remoteIp));
            expectRequest(testCase.get("request"), server);
        } finally {
            if (server != null) {
                server.close();
            }
        }
    }

    private interface Call {
        Verification verify() throws VerificationUnavailableException;
    }

    private static void expectOutcome(JsonNode expect, String secret, Call call) throws Exception {
        String outcome = expect.get("outcome").asText();
        switch (outcome) {
            case "passed": {
                Verification verification = call.verify();
                assertTrue(verification.isPassed());
                Verification.Passed passed = assertInstanceOf(Verification.Passed.class, verification);
                assertEquals(Optional.ofNullable(textOrNull(expect, "challengeTs")), passed.challengeTs());
                assertEquals(Optional.ofNullable(textOrNull(expect, "hostname")), passed.hostname());
                break;
            }
            case "rejected": {
                Verification verification = call.verify();
                assertFalse(verification.isPassed());
                Verification.Rejected rejected = assertInstanceOf(Verification.Rejected.class, verification);
                assertEquals(texts(expect.get("errorCodes")), rejected.errorCodes());
                break;
            }
            case "unavailable": {
                VerificationUnavailableException exception = assertThrows(VerificationUnavailableException.class, call::verify);
                for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                    assertFalse(cause.toString().contains(secret), "the secret key is in " + cause);
                }
                break;
            }
            default:
                fail("unknown outcome " + outcome);
        }
    }

    private static void expectRequest(JsonNode request, LocalSiteverify server) {
        if (request.isNull()) {
            if (server != null) {
                assertEquals(List.of(), server.requests(), "no request arrives");
            }
            return;
        }

        List<LocalSiteverify.Received> received = server.requests();
        assertEquals(1, received.size(), "one request arrives");
        assertEquals("POST", received.get(0).method);
        assertEquals("application/x-www-form-urlencoded", received.get(0).contentType);
        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> field : request.properties()) {
            fields.put(field.getKey(), field.getValue().asText());
        }
        assertEquals(fields, received.get(0).form);
    }

    private static JsonNode specification() throws IOException {
        try (InputStream in = SiteverifyCasesTest.class.getResourceAsStream("__fixtures__/siteverify-cases.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(element -> texts.add(element.asText()));
        return texts;
    }
}
