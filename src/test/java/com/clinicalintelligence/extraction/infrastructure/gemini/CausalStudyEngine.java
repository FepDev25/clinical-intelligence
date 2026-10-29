package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Per-unit causal workflow with explicit stage failure; neither transport nor secret loading. */
final class CausalStudyEngine {
    @FunctionalInterface interface StageExecutor {StudyTransport.Reply execute(GeminiProtocol p,ProviderRequest input,ObjectNode body,long end,String id) throws Exception;}
    static ObjectNode runUnit(JsonNode unit,JsonNode config,JsonNode request,boolean inference,StageExecutor executor) throws Exception {
        var json=new ObjectMapper();var result=json.createObjectNode().put("generationId",unit.path("generationId").asText()).put("caseId",unit.path("caseId").asText()).put("round",unit.path("round").asInt())
                .put("configuration",unit.path("configuration").asText()).put("syntheticData",true).put("inferenceExecuted",inference);
        var stages=result.putArray("stages");long start=System.nanoTime();
        boolean two=config.path("workflow").asText().equals("two-stage");
        long end=start+TimeUnit.SECONDS.toNanos(two?240:120);
        var input=new ProviderRequest(request.path("clinicalNote").asText(),request.path("locale").asText("es"));
        String model=config.path("model").asText();var level=GeminiGeneration.ThinkingLevel.valueOf(config.path("thinkingLevel").asText());
        try {
            if(two) {
                var detector=new GeminiProtocol(new GeminiGeneration(model,level,GeminiGeneration.Prompt.DETECTION));
                var first=execute(detector,input,detector.request(input,65536),end,unit.path("generationId").asText()+"/detection",executor,stages,json);
                if(first.decoded().completion()!=Completion.COMPLETE)return failed(result,"DETECTION_INVALID_RESPONSE");
                GeminiStageBoundary.detection(input.clinicalNote(),first.decoded().proposal());
                if(first.decoded().proposal().mentions().isEmpty())return complete(result,first,detector.identity().promptVersion());
                var classifier=new GeminiProtocol(new GeminiGeneration(model,level,GeminiGeneration.Prompt.CLASSIFICATION));
                var second=execute(classifier,input,classifier.classificationRequest(input,first.decoded().proposal(),65536),end,unit.path("generationId").asText()+"/classification",executor,stages,json);
                if(second.decoded().completion()!=Completion.COMPLETE)return failed(result,"CLASSIFICATION_INVALID_RESPONSE");
                GeminiStageBoundary.classification(first.decoded().proposal(),second.decoded().proposal());
                return complete(result,second,detector.identity().promptVersion()+"+"+classifier.identity().promptVersion());
            }
            var prompt=Arrays.stream(GeminiGeneration.Prompt.values()).filter(p -> p.version().equals(config.path("promptVersion").asText())).findFirst().orElseThrow();
            if(!prompt.singlePass())throw new IllegalArgumentException();
            var protocol=new GeminiProtocol(new GeminiGeneration(model,level,prompt));
            var reply=execute(protocol,input,protocol.request(input,65536),end,unit.path("generationId").asText()+"/single",executor,stages,json);
            if(reply.decoded().completion()!=Completion.COMPLETE)return failed(result,"GENERATION_INVALID_RESPONSE");
            return complete(result,reply,protocol.identity().promptVersion());
        } catch(StudyTransport.Stop stop){return result.put("status","stopped").put("stopCode",stop.code).put("httpStatus",stop.httpStatus);}
        catch(IllegalArgumentException boundary){return failed(result,"STAGE_BOUNDARY_REJECTED");}
        catch(Exception failure){return result.put("status","stopped").put("stopCode","INTERNAL_STUDY_FAILURE");}
        finally{result.put("latencyMillis",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));}
    }
    private static StudyTransport.Reply execute(GeminiProtocol p,ProviderRequest input,ObjectNode body,long end,String id,StageExecutor executor,com.fasterxml.jackson.databind.node.ArrayNode stages,ObjectMapper json) throws Exception {
        var r=executor.execute(p,input,body,end,id);var stage=stages.addObject().put("stageId",id).put("completion",r.decoded().completion().name()).put("model",r.decoded().modelVersion())
                .put("reportedModelVersion",r.reportedModelVersion()).put("chargedUsd",r.chargedUsd().toPlainString()).put("structuredProposalText",r.structuredProposalText());
        stage.set("identity",json.valueToTree(p.identity()));stage.set("usage",json.valueToTree(r.decoded().usage()));
        if(System.nanoTime()>end)throw new StudyTransport.Stop("UNIT_DEADLINE_EXCEEDED");return r;
    }
    private static ObjectNode complete(ObjectNode result,StudyTransport.Reply reply,String prompt) {
        return result.put("status","complete").put("structuredProposalText",reply.structuredProposalText()).put("promptVersion",prompt).put("model",reply.decoded().modelVersion());
    }
    private static ObjectNode failed(ObjectNode result,String code){return result.put("status","stage_failed").put("errorCode",code);}
}
