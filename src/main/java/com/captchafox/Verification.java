package com.captchafox;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of a token that siteverify evaluated: either {@link Passed} or {@link Rejected}, never both. A token
 * whose verification cannot be evaluated has no {@code Verification}; {@link CaptchaFoxClient#verify} throws
 * {@link VerificationUnavailableException} instead.
 */
public abstract class Verification {

    // Closes the hierarchy: Passed and Rejected are the only outcomes.
    private Verification() {
    }

    /**
     * Tells the outcomes apart.
     *
     * @return {@code true} for {@link Passed}, {@code false} for {@link Rejected}
     */
    public abstract boolean isPassed();

    /** siteverify answered {@code "success": true}. */
    public static final class Passed extends Verification {

        private final String challengeTs;
        private final String hostname;

        Passed(String challengeTs, String hostname) {
            this.challengeTs = challengeTs;
            this.hostname = hostname;
        }

        @Override
        public boolean isPassed() {
            return true;
        }

        /**
         * The time of the challenge as siteverify sent it.
         *
         * @return an ISO 8601 timestamp, if siteverify sent one
         */
        public Optional<String> challengeTs() {
            return Optional.ofNullable(challengeTs);
        }

        /**
         * The hostname of the site where the challenge was solved.
         *
         * @return the hostname, if siteverify sent one
         */
        public Optional<String> hostname() {
            return Optional.ofNullable(hostname);
        }

        @Override
        public String toString() {
            return "Passed[challengeTs=" + challengeTs + ", hostname=" + hostname + "]";
        }
    }

    /** siteverify answered {@code "success": false}, or the token was empty. */
    public static final class Rejected extends Verification {

        private final List<String> errorCodes;

        Rejected(List<String> errorCodes) {
            this.errorCodes = List.copyOf(errorCodes);
        }

        @Override
        public boolean isPassed() {
            return false;
        }

        /**
         * The error codes of siteverify, such as {@code timeout-or-duplicate}.
         *
         * @return the error codes, possibly none
         */
        public List<String> errorCodes() {
            return errorCodes;
        }

        @Override
        public String toString() {
            return "Rejected[errorCodes=" + errorCodes + "]";
        }
    }
}
