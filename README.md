# CaptchaFox for Java

Official [CaptchaFox](https://captchafox.com) integration for Java. It verifies the answers of the widget with
CaptchaFox.

## Compatibility

| Java | Library |
|---|---|
| 11, 17, 21, 25 | 1.x |

Java 10 and older are not supported: the JVM refuses to load the classes.

## Installation

Maven:

```xml
<dependency>
  <groupId>com.captchafox</groupId>
  <artifactId>captchafox-java</artifactId>
  <version>VERSION</version>
</dependency>
```

Gradle:

```kotlin
implementation("com.captchafox:captchafox-java:VERSION")
```

Replace `VERSION` with the latest version on
[Maven Central](https://central.sonatype.com/artifact/com.captchafox/captchafox-java). The library needs nothing but
the JDK, so without a build tool, put `captchafox-java-<version>.jar` from the
[releases](https://github.com/CaptchaFox/captchafox-java/releases) on the class path.

## Configuration

### Get your keys

In the [CaptchaFox portal](https://portal.captchafox.com), open your website under **Sites > Site Details** and copy
the **site key** and the **secret key**.

### Set up the library

Create a `CaptchaFoxClient` once with the secret key of your site and reuse it; it is thread-safe. Keep the secret key
in your configuration or environment, not in source code:

```java
CaptchaFoxClient client = new CaptchaFoxClient(System.getenv("CAPTCHAFOX_SECRET"));
```

### Options

| Option | Default | Meaning |
|---|---|---|
| `timeout` | 5 seconds | How long a verification may take, from connecting to CaptchaFox to the end of its answer: `new CaptchaFoxClient(secret, Duration.ofSeconds(3))` |

## Usage

The widget adds the token to the form as the POST parameter `cf-captcha-response`. Verify it on the server before
the form is processed:

```java
import com.captchafox.CaptchaFoxClient;
import com.captchafox.Verification;
import com.captchafox.VerificationUnavailableException;

String token = request.getParameter("cf-captcha-response");
try {
    Verification verification = client.verify(token, SITE_KEY, request.getRemoteAddr());
    if (!verification.isPassed()) {
        // Reject the form.
    }
} catch (VerificationUnavailableException e) {
    // CaptchaFox gave no usable answer: decide whether the form passes.
}
```

`verify(token)` sends the token alone; `verify(token, sitekey, remoteIp)` also sends the site key the token was
issued for and the visitor's IP address, each only when it is not `null` or empty.

A verification has one of three outcomes:

- `Verification.Passed`: CaptchaFox accepted the token. `challengeTs()` and `hostname()` hold the time of the
  challenge and the hostname of the site, where CaptchaFox sent them.
- `Verification.Rejected`: CaptchaFox rejected the token; `errorCodes()` holds its error codes, such as
  `timeout-or-duplicate`, possibly none. An empty or missing token is rejected with `missing-input-response` without
  asking CaptchaFox.
- `VerificationUnavailableException`: CaptchaFox gave no usable answer; see
  [If CaptchaFox is unreachable](#if-captchafox-is-unreachable).

## Operation

### If CaptchaFox is unreachable

When CaptchaFox cannot be reached, does not complete its answer within the timeout, answers with an HTTP status other
than 200, or with a body that is no valid answer, `verify` throws `VerificationUnavailableException`. Your code
decides whether the form passes then: letting it through keeps your forms available during an outage, rejecting it
keeps them protected. Tokens that CaptchaFox rejects are always `Verification.Rejected`.

### Log

The library logs nothing. The messages of its exceptions never contain the secret key.

### Visitor IP

The visitor's IP address is sent only when you pass it as `remoteIp`. Behind a reverse proxy or load balancer, pass
the visitor's address that the proxy forwards, not the address of the proxy.

### Privacy

CaptchaFox is operated by Scoria Labs GmbH, Germany. How CaptchaFox processes data and how to get a data processing
agreement is described in the [CaptchaFox Privacy Center](https://captchafox.com/privacy). A suggested paragraph for
your privacy policy is on the [GDPR page](https://captchafox.com/privacy/gdpr).

## License

Apache-2.0. See [LICENSE](LICENSE).
