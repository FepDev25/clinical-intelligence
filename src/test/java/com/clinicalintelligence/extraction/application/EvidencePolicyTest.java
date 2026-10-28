package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidencePolicyTest {
    private final EvidencePolicy policy = new EvidencePolicy();

    @Test void codePointOffsetsIncludeEmojiAndOriginalCrLf() {
        assertTrue(policy.validEvidence("🩺\r\nasma", new Evidence(3, 7, "asma")));
        assertFalse(policy.validEvidence("🩺\r\nasma", new Evidence(4, 8, "asma")));
    }

    @Test void quoteCannotBeBorrowedFromAnotherOccurrence() {
        assertTrue(policy.validEvidence("asma, asma", new Evidence(6, 10, "asma")));
        assertFalse(policy.validEvidence("asma, asma", new Evidence(1, 5, "asma")));
    }

    @Test void rejectsOutOfBoundsAndNonPositiveRangesWithoutThrowing() {
        for (Evidence span : List.of(new Evidence(-1, 3, "asma"), new Evidence(0, 100, "asma"),
                new Evidence(2, 2, "asma"), new Evidence(3, 1, "asma"))) {
            assertFalse(policy.validEvidence("asma", span));
        }
    }

    @Test void evidenceMustMatchOriginalUnicodeWithoutNormalization() {
        assertTrue(policy.validEvidence("cafe\u0301", new Evidence(0, 5, "cafe\u0301")));
        assertFalse(policy.validEvidence("cafe\u0301", new Evidence(0, 4, "café")));
    }

    @Test void textValueMustAppearInItsOwnValidEvidence() {
        assertTrue(policy.validText("dosis 5 mg", new Text("5 mg", List.of(new Evidence(0, 10, "dosis 5 mg")))));
        assertFalse(policy.validText("dosis 5 mg", new Text("9 mg", List.of(new Evidence(0, 10, "dosis 5 mg")))));
        assertFalse(policy.validText("asma", new Text("asma", List.of())));
    }

    @Test void everyAttachedSpanMustBeValid() {
        assertFalse(policy.validText("asma", new Text("asma", List.of(new Evidence(0, 4, "asma"), new Evidence(1, 4, "asma")))));
    }

    @Test void isolatedSurrogateCannotBorrowHalfOfAnEmojiAsTextValue() {
        assertFalse(policy.validText("🩺", new Text("\ud83e", List.of(new Evidence(0, 1, "🩺")))));
        assertTrue(policy.validText("🩺", new Text("🩺", List.of(new Evidence(0, 1, "🩺")))));
    }

    @Test void blankValuesAndMissingEvidenceAreNotGroundedText() {
        assertFalse(policy.validText("asma", new Text(" ", List.of(new Evidence(0, 4, "asma")))));
        assertFalse(policy.validText("asma", new Text(null, List.of(new Evidence(0, 4, "asma")))));
        assertFalse(policy.validText("asma", new Text("asma", null)));
    }
}
