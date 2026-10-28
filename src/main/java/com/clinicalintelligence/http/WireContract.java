package com.clinicalintelligence.http;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.EvidencePolicy;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Explicit transport boundary: no coercion and no clinical interpretation. */
public final class WireContract {
    private final ObjectMapper mapper;
    private final RegisteredLocale locales = new RegisteredLocale();

    public WireContract(int bodyBytes) {
        var factory = JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(20)
                        .maxStringLength(bodyBytes).maxNameLength(bodyBytes).build()).build();
        mapper = JsonMapper.builder(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    public Command decode(byte[] bytes) {
        JsonNode root;
        try {
            String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            root = mapper.readTree(json);
        } catch (Exception malformed) { throw new HttpFailure("MALFORMED_JSON"); }
        object(root, Set.of("clinicalNote","locale","catalogs"), "");
        String note = text(root.get("clinicalNote"), "/clinicalNote");
        String locale = root.has("locale") ? text(root.get("locale"), "/locale") : null;
        locale = locales.validate(locale);
        var catalogs = new ArrayList<Catalog>();
        JsonNode catalogNodes = root.get("catalogs");
        if (catalogNodes != null) {
            array(catalogNodes, "/catalogs");
            for (int i = 0; i < catalogNodes.size(); i++) {
                String pointer = "/catalogs/" + i;
                JsonNode node = catalogNodes.get(i);
                object(node, Set.of("catalogId","version","kind","entries"), pointer);
                String id = text(node.get("catalogId"), pointer + "/catalogId");
                String version = text(node.get("version"), pointer + "/version");
                String rawKind = text(node.get("kind"), pointer + "/kind");
                Kind kind = switch (rawKind) {
                    case "diagnosis" -> Kind.DIAGNOSIS;
                    case "medication" -> Kind.MEDICATION;
                    case "procedure" -> Kind.PROCEDURE;
                    default -> throw new HttpFailure("INVALID_REQUEST", pointer + "/kind");
                };
                JsonNode entryNodes = node.get("entries");
                array(entryNodes, pointer + "/entries");
                var entries = new ArrayList<CatalogEntry>();
                for (int j = 0; j < entryNodes.size(); j++) {
                    String ep = pointer + "/entries/" + j;
                    JsonNode entry = entryNodes.get(j);
                    object(entry, Set.of("entryId","name","aliases"), ep);
                    String entryId = text(entry.get("entryId"), ep + "/entryId");
                    String name = text(entry.get("name"), ep + "/name");
                    var aliases = new ArrayList<String>();
                    if (entry.has("aliases")) {
                        array(entry.get("aliases"), ep + "/aliases");
                        for (int k = 0; k < entry.get("aliases").size(); k++)
                            aliases.add(text(entry.get("aliases").get(k), ep + "/aliases/" + k));
                    }
                    entries.add(new CatalogEntry(entryId, name, aliases));
                }
                catalogs.add(new Catalog(id, version, kind, entries));
            }
        }
        return new Command(note, locale, catalogs);
    }

    private static void object(JsonNode node, Set<String> fields, String pointer) {
        if (node == null || !node.isObject()) throw new HttpFailure("INVALID_REQUEST", pointer);
        node.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw new HttpFailure("INVALID_REQUEST", pointer);
        });
    }
    private static void array(JsonNode node, String pointer) {
        if (node == null || !node.isArray()) throw new HttpFailure("INVALID_REQUEST", pointer);
    }
    private static String text(JsonNode node, String pointer) {
        if (node == null || !node.isTextual() || !EvidencePolicy.nonblankScalar(node.textValue()))
            throw new HttpFailure("INVALID_REQUEST", pointer);
        return node.textValue();
    }

    public ObjectNode encode(Result result, UUID requestId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("requestId", requestId.toString()).put("contractVersion",result.contractVersion())
                .put("locale",result.locale()).put("processingStatus",result.processingStatus())
                .put("reviewRequired",true);
        root.set("diagnoses", findings(result.diagnoses()));
        root.set("medications", findings(result.medications()));
        root.set("procedures", findings(result.procedures()));
        ArrayNode warnings = root.putArray("warnings");
        for (Warning warning : result.warnings()) {
            ObjectNode w = warnings.addObject().put("code",warning.code().name()).put("message",warning.message());
            w.set("targets",mapper.valueToTree(warning.targets()));
            w.put("source",warning.source());
        }
        Processing p = result.processing();
        root.putObject("processing").put("version",p.version()).put("promptVersion",p.promptVersion())
                .put("validationVersion",p.validationVersion()).put("executionMode",lower(p.executionMode()))
                .put("provider",p.provider()).put("model",p.model());
        return root;
    }

    private ArrayNode findings(List<Accepted> findings) {
        ArrayNode array = mapper.createArrayNode();
        for (Accepted accepted : findings) {
            Mention mention = accepted.mention();
            ObjectNode n = array.addObject().put("id",accepted.id());
            n.set("name",text(mention.name()));
            n.set("mentionedCode",text(mention.mentionedCode()));
            ObjectNode context = n.putObject("context");
            context.set("assertion",grounded(mention.context().assertion()));
            context.set("temporality",grounded(mention.context().temporality()));
            context.set("subject",grounded(mention.context().subject()));
            ObjectNode match = n.putObject("catalogMatch").put("status",lower(accepted.catalogMatch().status()));
            match.set("candidates",mapper.valueToTree(accepted.catalogMatch().candidates()));
            if (mention instanceof Medication med) {
                n.set("prescriptionStatus",grounded(med.prescriptionStatus()));
                n.set("dosage",text(med.dosage())); n.set("frequency",text(med.frequency()));
                n.set("route",text(med.route())); n.set("duration",text(med.duration()));
            } else if (mention instanceof Procedure procedure) n.set("performanceStatus",grounded(procedure.performanceStatus()));
        }
        return array;
    }
    private JsonNode text(Text value) {
        if (value == null) return NullNode.instance;
        ObjectNode node = mapper.createObjectNode().put("value",value.value());
        node.set("evidence",mapper.valueToTree(value.evidence())); return node;
    }
    private JsonNode grounded(Grounded<?> value) {
        ObjectNode node = mapper.createObjectNode().put("value",lower(value.value()));
        node.set("evidence",mapper.valueToTree(value.evidence())); return node;
    }
    private static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}

