package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.application.ExtractionException.Code.*;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.Objects;

public final class InputPolicy {
    private final InputLimits limits;
    public InputPolicy(InputLimits limits) { this.limits = Objects.requireNonNull(limits); }

    public String validate(Command command) {
        if (command == null || !nonblankScalar(command.clinicalNote()) || command.catalogs() == null)
            throw new ExtractionException(INVALID_REQUEST);
        if (command.clinicalNote().codePointCount(0, command.clinicalNote().length()) > limits.noteCodePoints()
                || command.catalogs().size() > limits.catalogs()) throw new ExtractionException(INPUT_LIMIT_EXCEEDED);
        String locale = command.locale() == null ? "es" : command.locale();
        // Locale.Builder parses iteratively; an unbounded repeated regex can
        // overflow the request thread stack before it rejects malformed tags.
        if (locale.isEmpty()) throw new ExtractionException(INVALID_LOCALE);
        try { new Locale.Builder().setLanguageTag(locale).build(); }
        catch (IllformedLocaleException invalid) { throw new ExtractionException(INVALID_LOCALE); }
        int separator = locale.indexOf('-');
        String language = separator < 0 ? locale : locale.substring(0, separator);
        if (!language.equalsIgnoreCase("es")) throw new ExtractionException(UNSUPPORTED_LOCALE);
        var catalogIds = new HashSet<String>();
        long entryCount = 0, aliasCount = 0;
        for (Catalog catalog : command.catalogs()) {
            if (catalog == null || !nonblankScalar(catalog.catalogId()) || !nonblankScalar(catalog.version())
                    || catalog.kind() == null || catalog.entries() == null || !catalogIds.add(catalog.catalogId()))
                throw new ExtractionException(INVALID_REQUEST);
            entryCount += catalog.entries().size();
            if (entryCount > limits.entries()) throw new ExtractionException(INPUT_LIMIT_EXCEEDED);
            var entryIds = new HashSet<String>();
            for (CatalogEntry entry : catalog.entries()) {
                if (entry == null || !nonblankScalar(entry.entryId()) || !nonblankScalar(entry.name())
                        || entry.aliases() == null || !entryIds.add(entry.entryId())) throw new ExtractionException(INVALID_REQUEST);
                aliasCount += entry.aliases().size();
                if (aliasCount > limits.aliases()) throw new ExtractionException(INPUT_LIMIT_EXCEEDED);
                if (entry.aliases().stream().anyMatch(alias -> !nonblankScalar(alias))) throw new ExtractionException(INVALID_REQUEST);
            }
        }
        return locale;
    }

    private static boolean nonblankScalar(String value) {
        return EvidencePolicy.nonblankScalar(value);
    }
}
