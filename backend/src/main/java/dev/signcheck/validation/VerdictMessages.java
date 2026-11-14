package dev.signcheck.validation;

import eu.europa.esig.dss.enumerations.SubIndication;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Turns a DSS sub-indication into a sentence a non-expert understands, in the caller's language
 * ({@code messages.properties} / {@code messages_es.properties}, key {@code reason.<SUB_INDICATION>}).
 *
 * <p>Sub-indications are defined by ETSI EN 319 102-1 and explain <em>why</em> a signature is not
 * {@code VALID}: {@code HASH_FAILURE} means the signed content changed, {@code NO_CERTIFICATE_CHAIN_FOUND}
 * that the signer is not linked to a trusted authority, and so on. The technical code is still returned
 * to API clients next to the text.
 */
@Component
public class VerdictMessages {

    static final String KEY_PREFIX = "reason.";

    private final MessageSource messageSource;

    public VerdictMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * @return the reason in {@code locale} (English if not available), or {@code null} without sub-indication
     */
    public String reason(SubIndication subIndication, Locale locale) {
        if (subIndication == null) {
            return null;
        }
        return messageSource.getMessage(KEY_PREFIX + subIndication.name(), null, subIndication.name(), locale);
    }
}
