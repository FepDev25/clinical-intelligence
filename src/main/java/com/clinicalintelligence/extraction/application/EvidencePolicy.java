package com.clinicalintelligence.extraction.application;

import com.clinicalintelligence.extraction.domain.ClinicalModel.Evidence;
import com.clinicalintelligence.extraction.domain.ClinicalModel.Text;

public final class EvidencePolicy {
    public boolean validEvidence(String note, Evidence evidence) {
        if (note == null || evidence == null || evidence.quote() == null
                || evidence.start() < 0 || evidence.end() <= evidence.start()
                || evidence.end() > note.codePointCount(0, note.length())) {
            return false;
        }
        int start = note.offsetByCodePoints(0, evidence.start());
        int end = note.offsetByCodePoints(0, evidence.end());
        return note.substring(start, end).equals(evidence.quote());
    }

    public boolean validText(String note, Text text) {
        return text != null && nonblankScalar(text.value()) && text.evidence() != null
                && !text.evidence().isEmpty()
                && text.evidence().stream().allMatch(e -> validEvidence(note, e))
                && text.evidence().stream().anyMatch(e -> e.quote().contains(text.value()));
    }

    public static boolean blank(String value) {
        return value == null || value.codePoints().allMatch(EvidencePolicy::whitespace);
    }

    public static boolean nonblankScalar(String value) {
        return !blank(value) && value.codePoints().noneMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF);
    }

    public static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }
}
