package dev.signcheck.validation.dto;

/**
 * The DSS reports as XML, for experts and for archiving (only returned when {@code includeRawReports=true}).
 *
 * @param simpleReport         DSS simple report: verdict per signature
 * @param detailedReport       DSS detailed report: every check of the ETSI EN 319 102-1 validation process
 * @param diagnosticData       DSS diagnostic data: every fact extracted from the document
 * @param etsiValidationReport validation report in the standard ETSI TS 119 102-2 format
 */
public record RawReports(String simpleReport, String detailedReport, String diagnosticData, String etsiValidationReport) {
}
