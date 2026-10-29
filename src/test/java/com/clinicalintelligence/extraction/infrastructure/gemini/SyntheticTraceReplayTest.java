package com.clinicalintelligence.extraction.infrastructure.gemini;

import com.clinicalintelligence.extraction.application.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

/** Local fixtures only. No GeminiProvider, credentials, network or Spring bootstrap. */
class SyntheticTraceReplayTest {
    static final class RecordingTrace implements ExtractionTrace {
        int structuredCalls;
        String structured;
        Proposal decoded;
        Result result;
        String failure;
        final List<MentionDecision> decisions = new ArrayList<>();
        @Override public void structuredProposal(String value) { structuredCalls++; structured = value; }
        @Override public void decodedProposal(Proposal value) { decoded = value; }
        @Override public void mentionDecision(MentionDecision value) { decisions.add(value); }
        @Override public void completed(Result value) { result = value; }
        @Override public void failed(String code) { failure = code; }
        @Override public String toString() { return "RecordingTrace[redacted]"; }
    }
    private Result replay(String note, String json, ExtractionTrace trace) throws Exception {
        byte[] envelope = GeminiProtocolTest.envelope(json, "STOP");
        var provider = (ExtractionProvider) request -> {
            var decoded = new GeminiProtocol().decode(request.clinicalNote(), envelope, trace);
            return new ProviderResponse(decoded.completion(), decoded.proposal(), metadata());
        };
        return new ExtractionService(provider).extract(Command.note(note), trace);
    }
    @Test void capturesStructuredDecodedAndFinalWithoutChangingOutcome() throws Exception {
        var trace = new RecordingTrace();
        String json = GeminiProtocolTest.proposal("asma", 0);
        var result = replay("Diagnóstico: asma.", json, trace);
        assertEquals(json, trace.structured);
        assertEquals("asma", trace.decoded.mentions().getFirst().name().value());
        assertSame(result, trace.result);
        assertNull(trace.failure);
        assertEquals("/diagnoses/0", trace.decisions.getFirst().outputPath());
        assertEquals(new ExtractionTrace.Source(0, Kind.DIAGNOSIS, 0), trace.decisions.getFirst().source());
        assertTrue(trace.decisions.getFirst().rejections().isEmpty());
    }
    @Test void mapsDiscardedSourceToAcceptedIndexAfterEarlierEntityDiscard() throws Exception {
        var trace = new RecordingTrace();
        String json = GeminiProtocolTest.proposal("asma", 0);
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) GeminiProtocolTest.JSON.readTree(json);
        var array = (com.fasterxml.jackson.databind.node.ArrayNode)tree.get("diagnoses");
        array.add(array.get(0).deepCopy());
        ((com.fasterxml.jackson.databind.node.ObjectNode)array.get(0).get("name")).put("value", "not-in-note");
        var result = replay("asma", tree.toString(), trace);
        assertEquals(1, result.diagnoses().size());
        assertEquals(0, trace.decisions.get(0).source().proposalIndex());
        assertNull(trace.decisions.get(0).outputPath());
        assertEquals(ExtractionTrace.Reason.NAME_LITERAL_REJECTED, trace.decisions.get(0).rejections().getFirst().reason());
        assertEquals(new ExtractionTrace.Source(1, Kind.DIAGNOSIS, 1), trace.decisions.get(1).source());
        assertEquals("/diagnoses/0", trace.decisions.get(1).outputPath());
    }
    @Test void invalidLocatorIsVisibleBeforeEntityIsDiscarded() throws Exception {
        var trace = new RecordingTrace();
        var result = replay("asma", GeminiProtocolTest.proposal("asma", 5), trace);
        assertTrue(trace.structured.contains("\"occurrence\":5"));
        assertEquals(-1, trace.decoded.mentions().getFirst().name().evidence().getFirst().start());
        assertTrue(result.findings().isEmpty());
        assertEquals(ExtractionTrace.Reason.NAME_LITERAL_REJECTED, trace.decisions.getFirst().rejections().getFirst().reason());
    }
    @Test void distinguishesOriginalUnknownFromContextSanitizedToUnknown() {
        var trace = new RecordingTrace();
        var original = diagnosis(NOTE);
        var bad = new Grounded<>(Assertion.AFFIRMED, List.of(new Evidence(-1, -1, "missing")));
        var context = new Context(bad, new Grounded<>(Temporality.UNKNOWN, List.of()), original.context().subject());
        var proposal = new Proposal(List.of(new Diagnosis(original.name(), null, context)), List.of());
        new ExtractionService(ignored -> completed(proposal)).extract(Command.note(NOTE), trace);
        assertEquals(Assertion.AFFIRMED, trace.decoded.mentions().getFirst().context().assertion().value());
        assertEquals(Assertion.UNKNOWN, trace.result.diagnoses().getFirst().mention().context().assertion().value());
        assertEquals(Temporality.UNKNOWN, trace.decoded.mentions().getFirst().context().temporality().value());
        assertEquals(List.of(new ExtractionTrace.Rejection("context/assertion", ExtractionTrace.Reason.CONTEXT_EVIDENCE_REJECTED)), trace.decisions.getFirst().rejections());
    }
    @Test void distinguishesOriginalNullLiteralRejectionAndAssociationRejection() {
        var trace = new RecordingTrace();
        String note = NOTE + " Control en 7 días.";
        var m = medication(note);
        var modified = new Medication(m.name(), new Text("invented", List.of(span(note,"asma"))), m.context(), m.prescriptionStatus(), m.dosage(), null, null, text(note,"7 días"));
        new ExtractionService(ignored -> completed(new Proposal(List.of(modified),List.of()))).extract(Command.note(note),trace);
        var accepted = (Medication) trace.result.medications().getFirst().mention();
        assertNull(accepted.mentionedCode()); assertNull(accepted.duration()); assertNull(accepted.frequency());
        assertEquals(Set.of(new ExtractionTrace.Rejection("mentionedCode",ExtractionTrace.Reason.TEXT_LITERAL_REJECTED),
                new ExtractionTrace.Rejection("duration",ExtractionTrace.Reason.MEDICATION_ASSOCIATION_REJECTED)), Set.copyOf(trace.decisions.getFirst().rejections()));
    }
    @Test void tracingRecordsTheOtherEntityCodeAssociationRejection() {
        var trace = new RecordingTrace();
        String note = NOTE + " Código de otro medicamento Beta: J02.";
        var m = medication(note);
        var modified = new Medication(m.name(), text(note,"J02"), m.context(), m.prescriptionStatus(), m.dosage(),null,null,null);
        new ExtractionService(ignored -> completed(new Proposal(List.of(modified),List.of()))).extract(Command.note(note),trace);
        assertNull(trace.result.medications().getFirst().mention().mentionedCode());
        assertEquals(List.of(new ExtractionTrace.Rejection("mentionedCode",ExtractionTrace.Reason.TEXT_ASSOCIATION_REJECTED)),trace.decisions.getFirst().rejections());
    }
    @Test void callbackFailureCannotChangeOutcomeOrLeakItsMessage() throws Exception {
        ExtractionTrace throwing = new ExtractionTrace() {
            private void fail() { throw new IllegalStateException("SYNTHETIC_PRIVATE_TRACE_SINK"); }
            @Override public void structuredProposal(String json) { fail(); }
            @Override public void decodedProposal(Proposal proposal) { fail(); }
            @Override public void mentionDecision(MentionDecision decision) { fail(); }
            @Override public void completed(Result result) { fail(); }
            @Override public void failed(String code) { fail(); }
        };
        var decoded = new GeminiProtocol().decode("asma",GeminiProtocolTest.envelope(GeminiProtocolTest.proposal("asma",0),"STOP"),throwing);
        var result = new ExtractionService(ignored -> new ProviderResponse(decoded.completion(),decoded.proposal(),metadata())).extract(Command.note("asma"),throwing);
        assertEquals(1,result.diagnoses().size());
        var service = new ExtractionService(ignored -> { throw new IllegalStateException("SYNTHETIC_PRIVATE_PROVIDER"); });
        var error = assertThrows(ExtractionException.class,() -> service.extract(Command.note("asma"),throwing));
        assertEquals(ExtractionException.Code.PROVIDER_UNAVAILABLE,error.code());
        assertFalse(error.toString().contains("SYNTHETIC_PRIVATE"));
    }
    @Test void collectorRejectsReuseRatherThanMixingOperations() {
        var trace = new SyntheticTraceCapture("reuse-check");
        var service = new ExtractionService(ignored -> completed(basic(NOTE)));
        service.extract(Command.note(NOTE),trace);
        assertTrue(trace.snapshot().path("captureValid").asBoolean());
        service.extract(Command.note(NOTE),trace);
        assertFalse(trace.snapshot().path("captureValid").asBoolean());
    }
    @Test void snapshotsDistinguishNullAndUnknownFromRejectedFields() {
        String note=NOTE+" Control en 7 días.";
        var m=medication(note);
        var altered=new Medication(m.name(),null,m.context(),m.prescriptionStatus(),m.dosage(),null,null,text(note,"7 días"));
        var trace=new SyntheticTraceCapture("field-transitions");
        new ExtractionService(ignored -> completed(new Proposal(List.of(altered),List.of()))).extract(Command.note(note),trace);
        var snapshot=trace.snapshot();
        var fields=new HashMap<String,com.fasterxml.jackson.databind.JsonNode>();
        snapshot.path("fieldTransitions").forEach(field -> fields.put(field.path("field").asText(),field));
        assertEquals("ORIGINAL_NULL",fields.get("frequency").path("disposition").asText());
        assertTrue(snapshot.path("fieldTransitions").toString().contains("REMOVED_TO_NULL"));
        assertTrue(snapshot.toString().contains("MEDICATION_ASSOCIATION_REJECTED"));
        assertFalse(trace.toString().contains("Alfa"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)snapshot).put("tampered", "synthetic");
        assertFalse(trace.snapshot().has("tampered"));
    }
    @Test void ordinaryAndFailurePathsDoNotLogClinicalContent() {
        var root=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();root.addAppender(appender);
        String marker="SYNTHETIC_T4_DO_NOT_LOG";
        try {
            new ExtractionService(ignored -> completed(basic(NOTE))).extract(Command.note(NOTE+marker));
            var failing=new ExtractionService(ignored -> { throw new IllegalStateException(marker); });
            assertThrows(ExtractionException.class,() -> failing.extract(Command.note(NOTE+marker)));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains(marker) || e.getFormattedMessage().contains("Alfa") || e.getThrowableProxy()!=null));
        } finally { root.detachAppender(appender);appender.stop(); }
    }
    @Test void replaysAndExportsOnlyExplicitLocalSyntheticFixtures() throws Exception {
        record Fixture(String id,String note,String json) {}
        String one=GeminiProtocolTest.proposal("asma",0);
        var doubled=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiProtocolTest.JSON.readTree(one);
        var diagnoses=(com.fasterxml.jackson.databind.node.ArrayNode)doubled.path("diagnoses");
        diagnoses.add(diagnoses.get(0).deepCopy());
        ((com.fasterxml.jackson.databind.node.ObjectNode)diagnoses.get(0).path("name")).put("value","not-in-note");
        String repeatNote="El paciente niega asma. Hoy se confirma asma en el paciente.";
        var wrongOccurrence=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiProtocolTest.JSON.readTree(one);
        var assertion=(com.fasterxml.jackson.databind.node.ObjectNode)wrongOccurrence.at("/diagnoses/0/context/assertion");
        assertion.put("value","affirmed");assertion.putArray("evidence").addObject().put("quote","Hoy se confirma asma en el paciente.").put("occurrence",0);
        var invalidContext=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiProtocolTest.JSON.readTree(one);
        var badAssertion=(com.fasterxml.jackson.databind.node.ObjectNode)invalidContext.at("/diagnoses/0/context/assertion");
        badAssertion.put("value","affirmed");badAssertion.putArray("evidence").addObject().put("quote","asma").put("occurrence",5);
        String medNote=NOTE+" Control en 7 días.";
        var m=medication(medNote);
        var originalContext=new Context(m.context().assertion(),new Grounded<>(Temporality.UNKNOWN,List.of()),m.context().subject());
        var rejected=new Medication(m.name(),new Text("invented",List.of(span(medNote,"asma"))),originalContext,m.prescriptionStatus(),m.dosage(),null,null,text(medNote,"7 días"));
        String codeNote=NOTE+" Código de otro medicamento Beta: J02.";
        var otherCode=new Medication(m.name(),text(codeNote,"J02"),m.context(),m.prescriptionStatus(),m.dosage(),null,null,null);
        var examples=List.of(
                new Fixture("preserved","asma",one),
                new Fixture("entity-index-remap","asma",doubled.toString()),
                new Fixture("invalid-occurrence","asma",GeminiProtocolTest.proposal("asma",5)),
                new Fixture("wrong-valid-occurrence",repeatNote,wrongOccurrence.toString()),
                new Fixture("invalid-context-occurrence","asma",invalidContext.toString()),
                new Fixture("provider-omission","Diagnóstico confirmado: asma.","{\"diagnoses\":[],\"medications\":[],\"procedures\":[],\"notices\":[]}"),
                new Fixture("unknown-null-and-discards",medNote,providerJson(medNote,new Proposal(List.of(rejected),List.of()))),
                new Fixture("other-entity-code",codeNote,providerJson(codeNote,new Proposal(List.of(otherCode),List.of()))),
                new Fixture("malformed-output","asma","{}"));
        String output=System.getProperty("ci.synthetic.trace.output");
        java.nio.file.Path directory=output==null ? null : java.nio.file.Path.of(output);
        if(directory!=null) java.nio.file.Files.createDirectories(directory);
        for(var fixture:examples) {
            var trace=new SyntheticTraceCapture(fixture.id());
            try { replay(fixture.note(),fixture.json(),trace); }
            catch(ExtractionException failure) { assertEquals("malformed-output",fixture.id()); }
            var snapshot=trace.snapshot();
            assertTrue(snapshot.path("captureValid").asBoolean(),fixture.id());
            assertTrue(snapshot.path("operationClosed").asBoolean(),fixture.id());
            if(directory!=null) java.nio.file.Files.writeString(directory.resolve(fixture.id()+".json"),
                    GeminiProtocolTest.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot)+"\n",
                    java.nio.file.StandardOpenOption.CREATE_NEW);
        }
    }
    private static String providerJson(String note,Proposal proposal) {
        var root=GeminiProtocolTest.JSON.createObjectNode();
        for(String family:List.of("diagnoses","medications","procedures"))root.putArray(family);
        assertTrue(proposal.notices().isEmpty());root.putArray("notices");
        for(var mention:proposal.mentions()) {
            var copy=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiProtocolTest.JSON.valueToTree(mention);
            for(String field:List.of("assertion","temporality","subject")) {
                var grounded=(com.fasterxml.jackson.databind.node.ObjectNode)copy.path("context").path(field);
                grounded.put("value",grounded.path("value").asText().toLowerCase(Locale.ROOT));
            }
            for(String field:List.of("prescriptionStatus","performanceStatus"))if(copy.has(field)) {
                var grounded=(com.fasterxml.jackson.databind.node.ObjectNode)copy.path(field);
                grounded.put("value",grounded.path("value").asText().toLowerCase(Locale.ROOT));
            }
            String family=switch(mention.kind()) {case DIAGNOSIS -> "diagnoses";case MEDICATION -> "medications";case PROCEDURE -> "procedures";};
            ((com.fasterxml.jackson.databind.node.ArrayNode)root.path(family)).add(GeminiProtocolTest.references(note,copy));
        }
        return root.toString();
    }
    @Test void neverCapturesThoughtPartsOrTransportEnvelope() throws Exception {
        var trace=new RecordingTrace();
        var root=(com.fasterxml.jackson.databind.node.ObjectNode)GeminiProtocolTest.JSON.readTree(
                GeminiProtocolTest.envelope(GeminiProtocolTest.proposal("asma",0),"STOP"));
        root.put("transportDebug","SYNTHETIC_TRANSPORT_SECRET");
        ((com.fasterxml.jackson.databind.node.ArrayNode)root.at("/candidates/0/content/parts")).insertObject(0)
                .put("thought",true).put("text","SYNTHETIC_REASONING_SECRET");
        new GeminiProtocol().decode("asma",GeminiProtocolTest.JSON.writeValueAsBytes(root),trace);
        assertNotNull(trace.structured);
        assertFalse(trace.structured.contains("SYNTHETIC_"));
        assertFalse(trace.structured.contains("usageMetadata"));
    }
    @Test void keepsFlatAndPerFamilyIndexesSeparateAcrossMixedProposal() {
        var m=medication(NOTE);
        var invalid=new Medication(new Text("not-in-note",m.name().evidence()),null,m.context(),m.prescriptionStatus(),m.dosage(),null,null,null);
        var trace=new RecordingTrace();
        new ExtractionService(ignored -> completed(new Proposal(List.of(invalid,diagnosis(NOTE),m),List.of()))).extract(Command.note(NOTE),trace);
        assertEquals(new ExtractionTrace.Source(0,Kind.MEDICATION,0),trace.decisions.get(0).source());
        assertNull(trace.decisions.get(0).outputPath());
        assertEquals(new ExtractionTrace.Source(1,Kind.DIAGNOSIS,0),trace.decisions.get(1).source());
        assertEquals(new ExtractionTrace.Source(2,Kind.MEDICATION,1),trace.decisions.get(2).source());
        assertEquals("/medications/0",trace.decisions.get(2).outputPath());
    }
    @Test void finalSnapshotUsesTheExistingWireMapperAndResolvableOutputPaths() {
        var trace=new SyntheticTraceCapture("wire-shaped-result");
        var result=new ExtractionService(ignored -> completed(basic(NOTE))).extract(Command.note(NOTE),trace);
        var snapshot=trace.snapshot();
        assertEquals("5 mg",snapshot.at("/finalResult/medications/0/dosage/value").asText());
        assertEquals("current",snapshot.at("/finalResult/medications/0/context/temporality/value").asText());
        assertEquals(result.requestId().toString(),snapshot.at("/finalResult/requestId").asText());
    }
    @Test void explicitTraceAndOrdinaryPathHaveEquivalentOutcomesExceptRequestUuid() throws Exception {
        var m=medication(NOTE);
        var altered=new Medication(m.name(),new Text("invented",List.of(span(NOTE,"asma"))),m.context(),m.prescriptionStatus(),m.dosage(),null,null,null);
        var invalid=new Diagnosis(new Text("not-in-note",diagnosis(NOTE).name().evidence()),null,diagnosis(NOTE).context());
        var proposal=new Proposal(List.of(invalid,altered,procedure(NOTE)),List.of());
        var service=new ExtractionService(ignored -> completed(proposal));
        var ordinary=service.extract(Command.note(NOTE));
        var traced=service.extract(Command.note(NOTE),new RecordingTrace());
        assertEquals(ordinary.findings(),traced.findings());
        assertEquals(ordinary.warnings(),traced.warnings());
        assertEquals(ordinary.processing(),traced.processing());
        assertEquals(ordinary.locale(),traced.locale());
        assertEquals(ordinary.processingStatus(),traced.processingStatus());
        byte[] envelope=GeminiProtocolTest.envelope(GeminiProtocolTest.proposal("asma",0),"STOP");
        var protocol=new GeminiProtocol();
        assertEquals(protocol.decode("asma",envelope),protocol.decode("asma",envelope,new RecordingTrace()));
    }
    @Test void malformedProviderOnlyEmitsControlledFailure() throws Exception {
        var trace = new RecordingTrace();
        assertThrows(ExtractionException.class,() -> replay("asma","{}",trace));
        assertNull(trace.structured);assertNull(trace.decoded);assertNull(trace.result);
        assertEquals("PROVIDER_INVALID_RESPONSE",trace.failure);
    }
}
