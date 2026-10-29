package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.infrastructure.gemini.GeminiProtocolTest.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
class StudySseTest {
    byte[] frame(String... chunks){return (": keepalive\r\n\r\n"+java.util.Arrays.stream(chunks).map(c -> "data: "+c+"\r\n\r\n").collect(java.util.stream.Collectors.joining())).getBytes(StandardCharsets.UTF_8);}
    @Test void assemblesPartialJsonPreservingUnicodeAndUsesFinalCumulativeUsage() throws Exception {
        String text=proposal("asma",0);int mid=text.length()/2;
        var one=JSON.createObjectNode().put("modelVersion",GeminiProtocol.MODEL);one.putArray("candidates").addObject().putObject("content").putArray("parts").addObject().put("text",text.substring(0,mid));
        var two=JSON.readTree(envelope(text.substring(mid),"STOP"));
        var assembled=StudySse.assemble(frame(one.toString(),two.toString()),GeminiProtocol.MODEL);
        assertEquals(2,assembled.events());assertEquals("STOP",assembled.finishReason());
        var decoded=new GeminiProtocol().decode("🩺 asma",assembled.envelope());assertEquals(com.clinicalintelligence.extraction.domain.ClinicalModel.Completion.COMPLETE,decoded.completion());assertEquals(100L,decoded.usage().inputTokens());
    }
    @Test void thoughtsAndSignaturesAreNotInTheReconstructedEnvelope() throws Exception {
        var data=JSON.readTree(envelope(proposal("asma",0),"STOP"));var parts=(com.fasterxml.jackson.databind.node.ArrayNode)data.at("/candidates/0/content/parts");
        parts.addObject().put("thought",true).put("text","sensitive synthetic thoughts");parts.addObject().put("thoughtSignature","sensitive synthetic signature");
        String combined=new String(StudySse.assemble(frame(data.toString()),GeminiProtocol.MODEL).envelope(),StandardCharsets.UTF_8);
        assertFalse(combined.contains("sensitive synthetic"));assertTrue(combined.contains("diagnoses"));
    }
    @Test void continuationIsTruncationAndNeverAnAutomaticSecondRequest() throws Exception {
        var combined=StudySse.assemble(frame(new String(envelope("{","CONTINUATION"),StandardCharsets.UTF_8)),GeminiProtocol.MODEL);
        assertEquals("CONTINUATION",combined.finishReason());assertEquals(com.clinicalintelligence.extraction.domain.ClinicalModel.Completion.TRUNCATED,new GeminiProtocol().decode("asma",combined.envelope()).completion());
    }
    @Test void foreignModelsOrNonSsePayloadAreRejected() throws Exception {
        var data=JSON.readTree(envelope(proposal("asma",0),"STOP"));((com.fasterxml.jackson.databind.node.ObjectNode)data).put("modelVersion","gemini-4-argon");
        assertThrows(StudyTransport.Stop.class,() -> StudySse.assemble(frame(data.toString()),GeminiProtocol.MODEL));
        assertThrows(StudyTransport.Stop.class,() -> StudySse.assemble(envelope(proposal("asma",0),"STOP"),GeminiProtocol.MODEL));
    }
    @Test void usageBeforeTheFinalFinishCannotBeMistakenForFinalBilling() throws Exception {
        var a=JSON.readTree(envelope("{","STOP"));((com.fasterxml.jackson.databind.node.ObjectNode)a.at("/candidates/0")).remove("finishReason");
        var b=JSON.createObjectNode();b.putArray("candidates").addObject().put("finishReason","STOP").putObject("content").putArray("parts").addObject().put("text","}");
        var out=StudySse.assemble(frame(a.toString(),b.toString()),GeminiProtocol.MODEL);
        assertNull(new GeminiProtocol().decode("asma",out.envelope()).usage().inputTokens());
    }
}
