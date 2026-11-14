package dev.signcheck.validation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Files to validate, decoupled from HTTP so the service does not depend on Spring MVC types.
 * Contents stay in memory and are never persisted.
 *
 * @param signedFile        the signed document (or the detached signature)
 * @param originals         original documents a detached signature refers to; empty when none were sent
 * @param includeRawReports whether to return the DSS XML reports
 * @param locale            language of the human-readable texts
 */
public record ValidationRequest(UploadedFile signedFile, List<UploadedFile> originals, boolean includeRawReports,
        Locale locale) {

    public ValidationRequest {
        Objects.requireNonNull(signedFile, "signedFile");
        originals = originals == null ? List.of() : List.copyOf(originals);
        locale = locale == null ? Locale.ENGLISH : locale;
    }

    /**
     * One uploaded file.
     *
     * @param name    original file name as sent by the client (may be {@code null})
     * @param content file bytes
     */
    public record UploadedFile(String name, byte[] content) {

        public UploadedFile {
            Objects.requireNonNull(content, "content");
        }
    }
}
