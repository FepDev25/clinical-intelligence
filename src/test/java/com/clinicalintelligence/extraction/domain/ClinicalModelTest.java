package com.clinicalintelligence.extraction.domain;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClinicalModelTest {
    @Test void callerCannotMutateEvidenceAfterCreatingProposal() {
        var source = new ArrayList<>(List.of(new Evidence(0, 4, "asma")));
        var text = new Text("asma", source);
        source.clear();
        assertEquals(1, text.evidence().size());
        assertThrows(UnsupportedOperationException.class, () -> text.evidence().clear());
    }

    @Test void clinicalTextAndCatalogContentsAreRedactedFromDiagnosticStrings() {
        String marker = "SYNTHETIC_SENSITIVE_MARKER";
        assertFalse(new Command(marker, "es", List.of()).toString().contains(marker));
        assertFalse(new Text(marker, List.of(new Evidence(0, marker.length(), marker))).toString().contains(marker));
        assertFalse(new CatalogEntry("id", marker, List.of(marker)).toString().contains(marker));
        assertFalse(new ProviderRequest(marker, "es").toString().contains(marker));
    }
}
