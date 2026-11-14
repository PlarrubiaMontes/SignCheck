package dev.signcheck.validation.dto;

import java.time.Instant;

/**
 * A timestamp attached to the signature. A timestamp is a token signed by a time-stamping authority (TSA,
 * RFC 3161) stating that some data (here: the signature value) existed at a given time.
 *
 * @param type           e.g. {@code SIGNATURE_TIMESTAMP}, {@code ARCHIVE_TIMESTAMP}, {@code DOCUMENT_TIMESTAMP}
 * @param productionTime time asserted by the TSA
 * @param tsaName        name of the TSA that produced it
 * @param valid          whether the timestamp itself validated (intact, correctly signed, trusted TSA)
 */
public record TimestampInfo(String type, Instant productionTime, String tsaName, boolean valid) {
}
