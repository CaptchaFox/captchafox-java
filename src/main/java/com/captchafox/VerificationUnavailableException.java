package com.captchafox;

/**
 * siteverify could not be evaluated: it was not reached, did not answer in time, answered with an HTTP status other
 * than 200, or with a body that is no JSON object with a boolean {@code success}. Whether the protected request
 * passes then is the caller's decision. The message never contains the secret key.
 */
public final class VerificationUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    VerificationUnavailableException(String message) {
        super(message);
    }

    VerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
