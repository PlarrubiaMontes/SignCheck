package dev.signcheck.validation.dto;

/**
 * Outcome of validating a signature, following the three <em>main indications</em> of
 * ETSI EN 319 102-1 (the European standard for AdES validation procedures).
 *
 * <ul>
 *   <li>{@link #VALID} — every check passed ({@code TOTAL_PASSED} / {@code PASSED} in DSS).</li>
 *   <li>{@link #INVALID} — a check failed for good: the signature can never become valid
 *       (for example the document was modified after signing: {@code HASH_FAILURE}).</li>
 *   <li>{@link #INDETERMINATE} — the checks did not fail, but the available information is not
 *       enough to conclude (no trust anchor, missing revocation data, missing signed data...).
 *       More data could turn it into {@code VALID} or {@code INVALID}.</li>
 * </ul>
 */
public enum Verdict {
    VALID,
    INDETERMINATE,
    INVALID
}
