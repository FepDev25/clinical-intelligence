package com.clinicalintelligence.http;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.ExtractionException;
import java.nio.charset.StandardCharsets;
import java.util.*;

class WireContractTest {
    final WireContract wire = new WireContract(67_108_864);
    Command read(String json) { return wire.decode(json.getBytes(StandardCharsets.UTF_8)); }

    @Test void defaultsOnlyOmittedFieldsAndPreservesOriginalNote() {
        var command = read("{\"clinicalNote\":\"  🩺\\r\\nnota  \"}");
        assertEquals("  🩺\r\nnota  ", command.clinicalNote());
        assertEquals("es", command.locale()); assertEquals(List.of(), command.catalogs());
        var catalog = read("{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"medication\",\"entries\":[{\"entryId\":\"x\",\"name\":\"A\"}]}]}").catalogs().getFirst();
        assertEquals(Kind.MEDICATION, catalog.kind()); assertEquals(List.of(), catalog.entries().getFirst().aliases());
    }

    @Test void rejectsCoercionsNullUnknownKeysAndDuplicatesAtEveryLevel() {
        String base = "{\"clinicalNote\":\"nota\"}";
        for (String json : List.of("null","[]","{}", "{\"clinicalNote\":null}","{\"clinicalNote\":42}",
                "{\"clinicalNote\":\"nota\",\"locale\":null}", "{\"clinicalNote\":\"nota\",\"catalogs\":null}",
                "{\"clinicalNote\":\"nota\",\"tenantId\":\"a\"}",
                "{\"clinicalNote\":\"nota\",\"clinicalNote\":\"otra\"}",
                "{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"DIAGNOSIS\",\"entries\":[]}]}",
                "{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"diagnosis\",\"entries\":[{\"entryId\":\"x\",\"name\":\"a\",\"aliases\":null}]}]}",
                "{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"diagnosis\",\"entries\":[{\"entryId\":\"x\",\"name\":\"a\",\"extra\":1}]}]}",
                "{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"a\",\"version\":\"1\",\"kind\":\"diagnosis\",\"entries\":[],\"extra\":0}]}",
                base+" {}", "{\"clinicalNote\":\"nota\",}")) {
            assertThrows(HttpFailure.class, () -> read(json), json);
        }
    }

    @Test void rejectsInvalidUtf8AndIsolatedSurrogates() {
        assertThrows(HttpFailure.class, () -> wire.decode(new byte[]{(byte)0xc0,(byte)0xaf}));
        assertThrows(HttpFailure.class, () -> read("{\"clinicalNote\":\"\\ud800\"}"));
        assertThrows(HttpFailure.class, () -> read("{\"clinicalNote\":\"nota\",\"catalogs\":[{\"catalogId\":\"\\ud800\",\"version\":\"1\",\"kind\":\"diagnosis\",\"entries\":[]}]}"));
    }

    @Test void mapsAllFamiliesWithExplicitNullsAndContractEnums() {
        var unknown = new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
        var text = new Text("A",List.of(new Evidence(0,1,"A")));
        var matches = new CatalogMatch(MatchStatus.NOT_PROVIDED,List.of());
        var result = new Result(UUID.randomUUID(),"es",List.of(
                new Accepted("d1",new Diagnosis(text,null,unknown),matches),
                new Accepted("m1",new Medication(text,null,unknown,new Grounded<>(PrescriptionStatus.UNKNOWN,List.of()),null,null,null,null),matches),
                new Accepted("p1",new Procedure(text,null,unknown,new Grounded<>(PerformanceStatus.NOT_PERFORMED,List.of(new Evidence(0,1,"A")))),matches)),
                List.of(),new Processing("core","prompt","validation",ExecutionMode.SIMULATED,"fake","none"));
        UUID requestId = UUID.randomUUID();
        var json = wire.encode(result,requestId);
        assertEquals(requestId.toString(),json.path("requestId").asText());
        assertFalse(json.has("findings"));
        assertEquals("not_performed",json.at("/procedures/0/performanceStatus/value").asText());
        assertTrue(json.at("/medications/0/duration").isNull());
        assertTrue(json.at("/diagnoses/0/mentionedCode").isNull());
        assertEquals("not_provided",json.at("/medications/0/catalogMatch/status").asText());
        assertEquals("simulated",json.at("/processing/executionMode").asText());
        assertEquals("unknown",json.at("/diagnoses/0/context/assertion/value").asText());
    }
}

