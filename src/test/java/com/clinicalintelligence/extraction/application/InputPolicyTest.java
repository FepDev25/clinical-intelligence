package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.application.ExtractionException.Code.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class InputPolicyTest {
    private final InputPolicy policy = new InputPolicy(InputLimits.defaults());

    @Test void defaultsLocaleButPreservesExplicitSpanishTagAndOriginalNote() {
        assertEquals("es", policy.validate(Command.note("  asma\r\n")));
        var command = new Command("  asma\r\n", "ES-ec", List.of());
        assertEquals("ES-ec", policy.validate(command));
        assertEquals("  asma\r\n", command.clinicalNote());
        assertEquals("es-419", policy.validate(new Command("asma", "es-419", List.of())));
    }

    @Test void invalidAndUnsupportedLocaleAreDifferentFailures() {
        assertCode(INVALID_LOCALE, new Command("asma", "es_EC", List.of()));
        assertCode(INVALID_LOCALE, new Command("asma", "es-u", List.of()));
        assertCode(UNSUPPORTED_LOCALE, new Command("asma", "en-US", List.of()));
    }

    @Test void rejectsBlankUnicodeAndUnpairedSurrogatesWithoutEchoingValues() {
        for (String note : List.of("", "\u00a0\u0085\u2003", "\ud800", "\udc00")) {
            var error = assertThrows(ExtractionException.class, () -> policy.validate(Command.note(note)));
            assertEquals(INVALID_REQUEST, error.code());
            assertEquals("INVALID_REQUEST", error.getMessage());
        }
        assertCode(INVALID_REQUEST, null);
        assertCode(INVALID_REQUEST, Command.note(null));
    }

    @Test void noteLimitsCountCodePointsAndNeverTruncate() {
        var small = new InputPolicy(new InputLimits(5, 1, 1, 1));
        var command = Command.note("🩺asma");
        assertEquals("es", small.validate(command));
        assertEquals("🩺asma", command.clinicalNote());
        assertEquals(INPUT_LIMIT_EXCEEDED, assertThrows(ExtractionException.class, () -> small.validate(Command.note("🩺asma!"))).code());
    }

    @Test void rejectsDuplicateCatalogIdsAndDuplicateEntryIdsBeforeResolution() {
        var entry = new CatalogEntry("same", "Alfa", List.of());
        var a = new Catalog("a", "1", Kind.MEDICATION, List.of(entry));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(a, a)));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(new Catalog("a", "1", Kind.MEDICATION, List.of(entry, entry)))));
        assertEquals("es", policy.validate(new Command("asma", "es", List.of(a, new Catalog("b", "1", Kind.MEDICATION, List.of(entry))))));
    }

    @Test void aggregateLimitsCountAllCatalogEntriesAndAliases() {
        var small = new InputPolicy(new InputLimits(100, 1, 1, 1));
        var a = new Catalog("a", "1", Kind.MEDICATION, List.of(new CatalogEntry("1", "Alfa", List.of("a", "b"))));
        assertEquals(INPUT_LIMIT_EXCEEDED, assertThrows(ExtractionException.class, () -> small.validate(new Command("asma", "es", List.of(a)))).code());
        var b = new Catalog("b", "1", Kind.MEDICATION, List.of());
        assertEquals(INPUT_LIMIT_EXCEEDED, assertThrows(ExtractionException.class, () -> small.validate(new Command("asma", "es", List.of(b, b)))).code());
        var entries = new Catalog("c", "1", Kind.MEDICATION, List.of(new CatalogEntry("1", "Alfa", List.of()), new CatalogEntry("2", "Beta", List.of())));
        assertEquals(INPUT_LIMIT_EXCEEDED, assertThrows(ExtractionException.class, () -> small.validate(new Command("asma", "es", List.of(entries)))).code());
    }

    @Test void rejectsMalformedCatalogDataAndExplicitNullCollections() {
        assertCode(INVALID_REQUEST, new Command("asma", "es", null));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(new Catalog("a", "1", null, List.of()))));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(new Catalog("a", "1", Kind.MEDICATION, null))));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(new Catalog("a", "1", Kind.MEDICATION, List.of(new CatalogEntry("x", "\u00a0", List.of()))))));
        assertCode(INVALID_REQUEST, new Command("asma", "es", List.of(new Catalog("a", "1", Kind.MEDICATION, List.of(new CatalogEntry("x", "Alfa", List.of("\ud800")))))));
    }

    @Test void refusesNonPositiveLimitConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new InputLimits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new InputLimits(1, 1, -1, 1));
    }

    private void assertCode(ExtractionException.Code code, Command command) {
        assertEquals(code, assertThrows(ExtractionException.class, () -> policy.validate(command)).code());
    }
}
