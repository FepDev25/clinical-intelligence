package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.ibm.icu.lang.UCharacter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class CatalogResolver {
    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::catalogId, CatalogResolver::compareCodePoints)
            .thenComparing(Candidate::version, CatalogResolver::compareCodePoints)
            .thenComparing(Candidate::entryId, CatalogResolver::compareCodePoints);
    private final Map<Kind, Map<String, List<Candidate>>> index = new EnumMap<>(Kind.class);

    /** A new index belongs to one validated request; no cross-consumer cache. */
    public CatalogResolver(List<Catalog> catalogs) {
        var building = new EnumMap<Kind, Map<String, TreeSet<Candidate>>>(Kind.class);
        for (Catalog catalog : catalogs) {
            var names = building.computeIfAbsent(catalog.kind(), ignored -> new HashMap<>());
            for (CatalogEntry entry : catalog.entries()) {
                var candidate = new Candidate(catalog.catalogId(), catalog.version(), entry.entryId());
                var aliases = new ArrayList<>(entry.aliases());
                aliases.add(entry.name());
                for (String name : aliases) {
                    names.computeIfAbsent(normalize(name), ignored -> new TreeSet<>(ORDER)).add(candidate);
                }
            }
        }
        building.forEach((kind, names) -> {
            Map<String, List<Candidate>> immutable = new HashMap<>();
            names.forEach((name, candidates) -> immutable.put(name, List.copyOf(candidates)));
            index.put(kind, Map.copyOf(immutable));
        });
    }

    public CatalogMatch resolve(Kind kind, String name) {
        if (!index.containsKey(kind)) return new CatalogMatch(MatchStatus.NOT_PROVIDED, List.of());
        List<Candidate> candidates = index.get(kind).getOrDefault(normalize(name), List.of());
        MatchStatus status = candidates.isEmpty() ? MatchStatus.NOT_FOUND
                : candidates.size() == 1 ? MatchStatus.MATCHED : MatchStatus.AMBIGUOUS;
        return new CatalogMatch(status, candidates);
    }

    private static String normalize(String value) {
        String folded = UCharacter.foldCase(Normalizer.normalize(value, Normalizer.Form.NFC), true);
        var normalized = new StringBuilder();
        boolean pendingSpace = false;
        for (int point : folded.codePoints().toArray()) {
            if (EvidencePolicy.whitespace(point)) {
                pendingSpace = !normalized.isEmpty();
            } else {
                if (pendingSpace) normalized.append(' ');
                normalized.appendCodePoint(point);
                pendingSpace = false;
            }
        }
        return normalized.toString();
    }

    private static int compareCodePoints(String left, String right) {
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            int a = left.codePointAt(i), b = right.codePointAt(j);
            if (a != b) return Integer.compare(a, b);
            i += Character.charCount(a);
            j += Character.charCount(b);
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }
}
