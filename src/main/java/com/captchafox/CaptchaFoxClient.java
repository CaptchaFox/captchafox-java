package com.captchafox;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Verifies tokens of the CaptchaFox widget with siteverify, with the secret key of a site. An instance is immutable
 * and thread-safe; create it once and reuse it.
 */
public final class CaptchaFoxClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    static final URI ENDPOINT = URI.create("https://api.captchafox.com/siteverify");

    private final String secret;
    private final Duration timeout;
    private final URI endpoint;
    private final HttpClient http;

    /**
     * Gives a verification 5 seconds, from connecting to siteverify to the end of its answer.
     *
     * @param secret the secret key of the site
     */
    public CaptchaFoxClient(String secret) {
        this(secret, DEFAULT_TIMEOUT);
    }

    /**
     * Gives a verification the timeout instead of 5 seconds.
     *
     * @param secret  the secret key of the site
     * @param timeout how long a verification may take, from connecting to siteverify to the end of its answer
     */
    public CaptchaFoxClient(String secret, Duration timeout) {
        this(secret, timeout, ENDPOINT);
    }

    CaptchaFoxClient(String secret, Duration timeout, URI endpoint) {
        Objects.requireNonNull(secret, "secret");
        Objects.requireNonNull(timeout, "timeout");
        if (secret.isEmpty()) {
            throw new IllegalArgumentException("the secret key is empty");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("the timeout is not positive");
        }

        this.secret = secret;
        this.timeout = timeout;
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    Duration timeout() {
        return timeout;
    }

    /**
     * Verifies a token.
     *
     * @param token the token the widget handed to the visitor's form
     * @return {@link Verification.Passed} or {@link Verification.Rejected}; an empty or {@code null} token is
     *         rejected with {@code missing-input-response} without a request
     * @throws VerificationUnavailableException if siteverify cannot be evaluated
     */
    public Verification verify(String token) throws VerificationUnavailableException {
        return verify(token, null, null);
    }

    /**
     * Verifies a token that was issued for a site key to a visitor's IP address.
     *
     * @param token    the token the widget handed to the visitor's form
     * @param sitekey  the site key the token was issued for; not sent when {@code null} or empty
     * @param remoteIp the IP address of the visitor; not sent when {@code null} or empty
     * @return {@link Verification.Passed} or {@link Verification.Rejected}; an empty or {@code null} token is
     *         rejected with {@code missing-input-response} without a request
     * @throws VerificationUnavailableException if siteverify cannot be evaluated
     */
    public Verification verify(String token, String sitekey, String remoteIp) throws VerificationUnavailableException {
        if (token == null || token.isEmpty()) {
            return new Verification.Rejected(List.of("missing-input-response"));
        }

        StringBuilder form = new StringBuilder();
        appendField(form, "secret", secret);
        appendField(form, "response", token);
        appendField(form, "sitekey", sitekey);
        appendField(form, "remoteIp", remoteIp);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();

        // One deadline over connect, headers and body: the timeout of the request ends when the headers arrive.
        CancellableBody body = new CancellableBody();
        CompletableFuture<HttpResponse<String>> pending = http.sendAsync(request, info -> body);
        HttpResponse<String> response;
        try {
            response = pending.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            body.cancel();
            pending.cancel(true);
            throw new VerificationUnavailableException("siteverify did not answer within " + timeout, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof HttpTimeoutException) {
                throw new VerificationUnavailableException("siteverify did not answer within " + timeout, e.getCause());
            }
            throw new VerificationUnavailableException("siteverify could not be reached", e.getCause());
        } catch (InterruptedException e) {
            body.cancel();
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new VerificationUnavailableException("interrupted while waiting for siteverify", e);
        }

        if (response.statusCode() != 200) {
            throw new VerificationUnavailableException("siteverify answered with HTTP status " + response.statusCode());
        }

        return evaluate(response.body());
    }

    static Verification evaluate(String body) throws VerificationUnavailableException {
        Object answer;
        try {
            answer = Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new VerificationUnavailableException("siteverify answered with a body that is no JSON", e);
        }

        if (!(answer instanceof Map)) {
            throw new VerificationUnavailableException("siteverify answered with JSON that is no object");
        }
        Map<?, ?> fields = (Map<?, ?>) answer;

        Object success = fields.get("success");
        if (!(success instanceof Boolean)) {
            throw new VerificationUnavailableException("siteverify answered without a boolean success");
        }

        if ((Boolean) success) {
            return new Verification.Passed(stringOrNull(fields.get("challenge_ts")), stringOrNull(fields.get("hostname")));
        } else {
            return new Verification.Rejected(strings(fields.get("error-codes")));
        }
    }

    // Java 11 aborts an exchange whose headers arrived only when its body subscription is cancelled; cancelling the
    // future does not.
    private static final class CancellableBody implements HttpResponse.BodySubscriber<String> {

        private final HttpResponse.BodySubscriber<String> text = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        private Flow.Subscription subscription;
        private boolean cancelled;

        @Override
        public CompletionStage<String> getBody() {
            return text.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            synchronized (this) {
                this.subscription = subscription;
                if (cancelled) {
                    subscription.cancel();
                    return;
                }
            }
            text.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            text.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            text.onError(throwable);
        }

        @Override
        public void onComplete() {
            text.onComplete();
        }

        synchronized void cancel() {
            cancelled = true;
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

    private static void appendField(StringBuilder form, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }

        if (form.length() > 0) {
            form.append('&');
        }
        form.append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static String stringOrNull(Object value) {
        return value instanceof String ? (String) value : null;
    }

    // A rejection stays a rejection when its error codes are malformed; only the entries that are strings are kept.
    private static List<String> strings(Object value) {
        List<String> strings = new ArrayList<>();
        if (!(value instanceof List)) {
            return strings;
        }

        for (Object element : (List<?>) value) {
            if (element instanceof String) {
                strings.add((String) element);
            }
        }
        return strings;
    }
}
