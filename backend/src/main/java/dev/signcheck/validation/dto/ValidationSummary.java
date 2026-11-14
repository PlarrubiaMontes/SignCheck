package dev.signcheck.validation.dto;

/**
 * Document-level summary.
 *
 * @param signatureCount number of signatures found
 * @param validCount     number of signatures with verdict {@link Verdict#VALID}
 * @param overallVerdict {@code VALID} only if every signature is valid; otherwise {@code INVALID} if
 *                       any signature is invalid, else {@code INDETERMINATE}
 */
public record ValidationSummary(int signatureCount, int validCount, Verdict overallVerdict) {
}
