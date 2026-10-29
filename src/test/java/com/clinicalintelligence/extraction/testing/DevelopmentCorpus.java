package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Test-only decoder of the existing development revision 1.0.1 (Brisa anchor corrected).
 * Historical v1 remains untouched. Runtime never loads gold or corpus IDs. */
public final class DevelopmentCorpus {
    private DevelopmentCorpus() {}
    public record Case(String id, Command command, Proposal proposal, List<CatalogMatch> matches, List<Warning> requiredWarnings) {}

    public static List<Case> load() throws IOException {
        try (var stream = DevelopmentCorpus.class.getResourceAsStream("/corpus-v1.0.1/development.json")) {
            if (stream == null) throw new IOException("Development fixture resource missing");
            JsonNode root = new ObjectMapper().readTree(stream);
            var cases = new ArrayList<Case>();
            for (JsonNode item : root) {
                JsonNode request = item.path("request"), expected = item.path("expected");
                var mentions = new ArrayList<Mention>();
                var matches = new ArrayList<CatalogMatch>();
                for (String collection : List.of("diagnoses", "medications", "procedures")) {
                    for (JsonNode entity : expected.path(collection)) {
                        mentions.add(mention(collection, entity));
                        matches.add(match(entity.path("catalogMatch")));
                    }
                }
                var notices = new ArrayList<Notice>();
                var warnings = new ArrayList<Warning>();
                for (JsonNode warning : expected.path("requiredWarnings")) {
                    WarningCode code = WarningCode.valueOf(warning.path("code").asText());
                    var targets = new ArrayList<Target>();
                    var pointers = new ArrayList<String>();
                    for (JsonNode pointer : warning.path("targets")) {
                        String value = pointer.asText();
                        pointers.add(value);
                        String[] path = value.split("/", 4);
                        int base = switch (path[1]) {
                            case "diagnoses" -> 0;
                            case "medications" -> expected.path("diagnoses").size();
                            case "procedures" -> expected.path("diagnoses").size() + expected.path("medications").size();
                            default -> throw new IllegalArgumentException("Unknown fixture collection");
                        };
                        targets.add(new Target(base + Integer.parseInt(path[2]), field(path.length == 3 ? "" : path[3])));
                    }
                    notices.add(new Notice(code, targets));
                    warnings.add(new Warning(code, pointers, "/clinicalNote"));
                }
                var command = new Command(request.path("clinicalNote").asText(),
                        request.has("locale") ? request.path("locale").asText() : null, catalogs(request.path("catalogs")));
                cases.add(new Case(item.path("id").asText(), command, new Proposal(mentions, notices), List.copyOf(matches), List.copyOf(warnings)));
            }
            return List.copyOf(cases);
        }
    }

    private static Mention mention(String collection, JsonNode node) {
        Text name = text(node.get("name")), code = text(node.get("mentionedCode"));
        var ctx = node.path("context");
        var context = new Context(grounded(ctx.path("assertion"), Assertion.class), grounded(ctx.path("temporality"), Temporality.class), grounded(ctx.path("subject"), Subject.class));
        return switch (collection) {
            case "diagnoses" -> new Diagnosis(name, code, context);
            case "medications" -> new Medication(name, code, context, grounded(node.path("prescriptionStatus"), PrescriptionStatus.class),
                    text(node.get("dosage")), text(node.get("frequency")), text(node.get("route")), text(node.get("duration")));
            case "procedures" -> new Procedure(name, code, context, grounded(node.path("performanceStatus"), PerformanceStatus.class));
            default -> throw new IllegalArgumentException("Unknown fixture kind");
        };
    }

    private static Text text(JsonNode node) {
        return node == null || node.isNull() ? null : new Text(node.path("value").asText(), evidence(node.path("evidence")));
    }
    private static <T extends Enum<T>> Grounded<T> grounded(JsonNode node, Class<T> type) {
        return new Grounded<>(Enum.valueOf(type, node.path("value").asText().toUpperCase(Locale.ROOT)), evidence(node.path("evidence")));
    }
    private static List<Evidence> evidence(JsonNode node) {
        var spans = new ArrayList<Evidence>();
        for (JsonNode span : node) spans.add(new Evidence(span.path("start").asInt(), span.path("end").asInt(), span.path("quote").asText()));
        return List.copyOf(spans);
    }
    private static List<Catalog> catalogs(JsonNode node) {
        var catalogs = new ArrayList<Catalog>();
        for (JsonNode catalog : node) {
            var entries = new ArrayList<CatalogEntry>();
            for (JsonNode entry : catalog.path("entries")) {
                var aliases = new ArrayList<String>();
                entry.path("aliases").forEach(alias -> aliases.add(alias.asText()));
                entries.add(new CatalogEntry(entry.path("entryId").asText(), entry.path("name").asText(), aliases));
            }
            catalogs.add(new Catalog(catalog.path("catalogId").asText(), catalog.path("version").asText(),
                    Kind.valueOf(catalog.path("kind").asText().toUpperCase(Locale.ROOT)), entries));
        }
        return List.copyOf(catalogs);
    }
    private static CatalogMatch match(JsonNode node) {
        var candidates = new ArrayList<Candidate>();
        for (JsonNode c : node.path("candidates")) candidates.add(new Candidate(c.path("catalogId").asText(), c.path("version").asText(), c.path("entryId").asText()));
        return new CatalogMatch(MatchStatus.valueOf(node.path("status").asText().toUpperCase(Locale.ROOT)), candidates);
    }
    private static Field field(String path) {
        return switch (path) {
            case "" -> Field.ENTITY;
            case "context/assertion" -> Field.ASSERTION;
            case "context/temporality" -> Field.TEMPORALITY;
            case "context/subject" -> Field.SUBJECT;
            case "prescriptionStatus" -> Field.PRESCRIPTION_STATUS;
            case "performanceStatus" -> Field.PERFORMANCE_STATUS;
            case "mentionedCode" -> Field.MENTIONED_CODE;
            case "dosage" -> Field.DOSAGE;
            case "frequency" -> Field.FREQUENCY;
            case "route" -> Field.ROUTE;
            case "duration" -> Field.DURATION;
            default -> throw new IllegalArgumentException("Unknown fixture pointer");
        };
    }
}
