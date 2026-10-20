## 1.0.0

- Verify CaptchaFox tokens with siteverify: `CaptchaFoxClient.verify` returns `Passed` or `Rejected`, and throws
  `VerificationUnavailableException` when CaptchaFox cannot be reached in time or answers unexpectedly.
- Java 11 and later, no runtime dependencies.
