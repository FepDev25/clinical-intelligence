package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogResolverTest {
    private Catalog catalog(Kind kind, CatalogEntry... entries) {
        return new Catalog("catalog", "v1", kind, List.of(entries));
    }

    @Test void distinguishesMissingCatalogFromExplicitEmptyCatalog() {
        assertEquals(MatchStatus.NOT_PROVIDED, new CatalogResolver(List.of()).resolve(Kind.MEDICATION, "Alfa").status());
        assertEquals(MatchStatus.NOT_FOUND, new CatalogResolver(List.of(catalog(Kind.MEDICATION))).resolve(Kind.MEDICATION, "Alfa").status());
    }

    @Test void matchesOnlyTheRequestedKind() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.DIAGNOSIS, new CatalogEntry("d", "Alfa", List.of()))));
        assertEquals(MatchStatus.MATCHED, resolver.resolve(Kind.DIAGNOSIS, "Alfa").status());
        assertEquals(MatchStatus.NOT_PROVIDED, resolver.resolve(Kind.MEDICATION, "Alfa").status());
    }

    @Test void normalizesNfcFullCaseFoldingAndUnicodeSpaces() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.MEDICATION,
                new CatalogEntry("m", "otra presentación", List.of("\u0085 A\u0301MBAR\u00a0  10 mg ", "Straße")))));
        assertEquals(List.of(new Candidate("catalog", "v1", "m")), resolver.resolve(Kind.MEDICATION, "ámbar 10 mg").candidates());
        assertEquals(MatchStatus.MATCHED, resolver.resolve(Kind.MEDICATION, "STRASSE").status());
    }

    @Test void doesNotEraseAccentsDosageRouteOrPunctuation() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.MEDICATION,
                new CatalogEntry("m", "Ciprés-10 mg oral", List.of()))));
        for (String name : List.of("Cipres-10 mg oral", "Ciprés-20 mg oral", "Ciprés-10 mg tópica", "Ciprés 10 mg oral")) {
            assertEquals(MatchStatus.NOT_FOUND, resolver.resolve(Kind.MEDICATION, name).status());
        }
    }

    @Test void noImplicitMedicalEquivalence() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.MEDICATION,
                new CatalogEntry("m", "paracetamol", List.of()))));
        assertEquals(MatchStatus.NOT_FOUND, resolver.resolve(Kind.MEDICATION, "acetaminofén").status());
    }

    @Test void duplicateAliasesToSameEntryAreOneCandidate() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.MEDICATION,
                new CatalogEntry("m", "Alfa", List.of("ALFA", "  Alfa  ")))));
        assertEquals(new CatalogMatch(MatchStatus.MATCHED, List.of(new Candidate("catalog", "v1", "m"))), resolver.resolve(Kind.MEDICATION, "Alfa"));
    }

    @Test void returnsAllAmbiguousCandidatesInCodePointOrder() {
        var resolver = new CatalogResolver(List.of(catalog(Kind.MEDICATION,
                new CatalogEntry("🩺", "Alfa", List.of()), new CatalogEntry("\ue000", "Alfa", List.of()))));
        var match = resolver.resolve(Kind.MEDICATION, "Alfa");
        assertEquals(MatchStatus.AMBIGUOUS, match.status());
        assertEquals(List.of(new Candidate("catalog", "v1", "\ue000"), new Candidate("catalog", "v1", "🩺")), match.candidates());
        assertThrows(UnsupportedOperationException.class, () -> match.candidates().clear());
    }

    @Test void equalEntryIdsInDifferentCatalogsRemainAmbiguous() {
        var resolver = new CatalogResolver(List.of(
                new Catalog("b", "1", Kind.MEDICATION, List.of(new CatalogEntry("id", "Alfa", List.of()))),
                new Catalog("a", "2", Kind.MEDICATION, List.of(new CatalogEntry("id", "Alfa", List.of())))));
        assertEquals(List.of(new Candidate("a", "2", "id"), new Candidate("b", "1", "id")), resolver.resolve(Kind.MEDICATION, "Alfa").candidates());
    }
}
