package dev.signcheck.validation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.signcheck.validation.dto.SignatureResult;
import dev.signcheck.validation.dto.ValidationSummary;
import dev.signcheck.validation.dto.Verdict;
import eu.europa.esig.dss.enumerations.Indication;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ValidationReportMapperTest {

    @ParameterizedTest
    @CsvSource({
            "TOTAL_PASSED, VALID",
            "PASSED, VALID",
            "INDETERMINATE, INDETERMINATE",
            "TOTAL_FAILED, INVALID",
            "FAILED, INVALID"})
    void shouldMapDssIndicationsToVerdicts(Indication indication, Verdict expected) {
        assertThat(ValidationReportMapper.toVerdict(indication)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(Indication.class)
    void shouldMapEveryDssIndication(Indication indication) {
        assertThat(ValidationReportMapper.toVerdict(indication)).isNotNull();
    }

    @Test
    void shouldSummariseAsValidOnlyWhenEverySignatureIsValid() {
        assertThat(summary(Verdict.VALID, Verdict.VALID))
                .isEqualTo(new ValidationSummary(2, 2, Verdict.VALID));
    }

    @Test
    void shouldSummariseAsInvalidWhenAnySignatureIsInvalid() {
        assertThat(summary(Verdict.VALID, Verdict.INDETERMINATE, Verdict.INVALID))
                .isEqualTo(new ValidationSummary(3, 1, Verdict.INVALID));
    }

    @Test
    void shouldSummariseAsIndeterminateWhenNoneIsInvalidButNotAllAreValid() {
        assertThat(summary(Verdict.VALID, Verdict.INDETERMINATE))
                .isEqualTo(new ValidationSummary(2, 1, Verdict.INDETERMINATE));
    }

    private static ValidationSummary summary(Verdict... verdicts) {
        List<SignatureResult> signatures = Arrays.stream(verdicts)
                .map(verdict -> new SignatureResult("S-1", verdict, null, null, "PAdES", "PAdES-BASELINE-B", "N/A",
                        null, null, null, null, List.of(), null, List.of(), List.of(), List.of()))
                .toList();
        return ValidationReportMapper.summarise(signatures);
    }
}
