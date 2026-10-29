package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Offline study orchestration. No HTTP client, API key, Spring bean or production selection. */
public final class GeminiTwoStageExperiment {
    @FunctionalInterface public interface Reply {byte[] generate(GeminiProtocol protocol,ObjectNode request) throws Exception;}
    public record Stage(GeminiProtocol.Identity identity,Completion completion,GeminiProtocol.Usage usage,String effectiveModel,long latencyMillis) {}
    public static final class StageFailure extends IllegalArgumentException {
        private final List<Stage> stages;
        StageFailure(String code,List<Stage> stages){super(code);this.stages=List.copyOf(stages);}
        public List<Stage> stages(){return stages;}
    }
    public record Outcome(ProviderResponse response,List<Stage> stages) {
        public Outcome {stages=List.copyOf(stages);}
        public Long inputTokens(){long sum=0;for(var s:stages){if(s.usage().inputTokens()==null)return null;sum=Math.addExact(sum,s.usage().inputTokens());}return sum;}
        public Long outputAndThoughtTokens(){long sum=0;for(var s:stages){if(s.usage().outputTokens()==null || s.usage().thoughtTokens()==null)return null;sum=Math.addExact(sum,Math.addExact(s.usage().outputTokens(),s.usage().thoughtTokens()));}return sum;}
    }
    private final GeminiProtocol detection,classification;
    public GeminiTwoStageExperiment(GeminiGeneration.ThinkingLevel level) {
        detection=new GeminiProtocol(new GeminiGeneration(GeminiProtocol.MODEL,level,GeminiGeneration.Prompt.DETECTION));
        classification=new GeminiProtocol(new GeminiGeneration(GeminiProtocol.MODEL,level,GeminiGeneration.Prompt.CLASSIFICATION));
    }
    public Outcome run(ProviderRequest request,int tokens,Reply reply) throws Exception {
        var stages=new ArrayList<Stage>();var first=stage(detection,request,detection.request(request,tokens),reply,stages);
        try {GeminiStageBoundary.detection(request.clinicalNote(),first.proposal());}
        catch(IllegalArgumentException invalid){throw new StageFailure("STAGE_BOUNDARY_REJECTED",stages);}
        if(first.proposal().mentions().isEmpty())return outcome(first,stages,detection.identity().promptVersion());
        var second=stage(classification,request,classification.classificationRequest(request,first.proposal(),tokens),reply,stages);
        try {GeminiStageBoundary.classification(first.proposal(),second.proposal());}
        catch(IllegalArgumentException invalid){throw new StageFailure("STAGE_BOUNDARY_REJECTED",stages);}
        return outcome(second,stages,detection.identity().promptVersion()+"+"+classification.identity().promptVersion());
    }
    private static GeminiProtocol.Decoded stage(GeminiProtocol protocol,ProviderRequest input,ObjectNode request,Reply reply,List<Stage> stages) throws Exception {
        long start=System.nanoTime();byte[] bytes;
        try {bytes=reply.generate(protocol,request);}
        catch(Exception failure) {
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            stages.add(new Stage(protocol.identity(),Completion.MALFORMED,new GeminiProtocol.Usage(null,null,null,null),null,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));
            throw new StageFailure("STAGE_TRANSPORT_FAILURE",stages);
        }
        var output=protocol.decode(input.clinicalNote(),bytes);
        stages.add(new Stage(protocol.identity(),output.completion(),output.usage(),output.modelVersion(),TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));
        if(output.completion()!=Completion.COMPLETE)throw new StageFailure("STAGE_INVALID_RESPONSE",stages);
        return output;
    }
    private static Outcome outcome(GeminiProtocol.Decoded output,List<Stage> stages,String prompt) {
        return new Outcome(new ProviderResponse(Completion.COMPLETE,output.proposal(),new ProviderMetadata(prompt,ExecutionMode.SIMULATED,"study-double",output.modelVersion())),stages);
    }
}
