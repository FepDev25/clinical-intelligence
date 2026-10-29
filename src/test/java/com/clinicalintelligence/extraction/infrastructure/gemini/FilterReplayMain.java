package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.*;
import com.clinicalintelligence.http.WireContract;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

/** Pure local replay: classpath selects the actual frozen filter, with no credential or HTTP access. */
public final class FilterReplayMain {
    public static void main(String[] args) {
        try {
            if(args.length!=2)throw new IllegalArgumentException();
            var json=new ObjectMapper();var data=json.readTree(Files.readAllBytes(Path.of(args[0])));
            var result=data.isArray()?batch(data,json):replay(data,json);
            Files.writeString(Path.of(args[1]),json.writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n",StandardOpenOption.CREATE_NEW);
        } catch(Exception invalid){System.err.println("FILTER_REPLAY_FAILED");System.exit(2);}
    }
    static JsonNode batch(JsonNode inputs,ObjectMapper json) throws Exception {
        var results=json.createArrayNode();
        for(var input:inputs) {
            ObjectNode out;
            try {out=replay(input,json);}
            catch(ExtractionException invalid) {
                out=json.createObjectNode().put("status",502).put("sourceFilterVersion",actualFilterVersion());
                out.putObject("body").put("code",invalid.code().name());
            }
            for(String key:List.of("generationId","caseId","round","armId"))out.set(key,input.path(key).deepCopy());
            results.add(out);
        }
        return results;
    }
    private static String actualFilterVersion() {
        var metadata=new ProviderMetadata("local-version-probe",ExecutionMode.SIMULATED,"local-probe","none");
        var result=new ExtractionService(ignored -> new ProviderResponse(Completion.COMPLETE,new Proposal(List.of(),List.of()),metadata)).extract(Command.note("Comprobación sintética de versión del filtro."));
        return result.processing().validationVersion();
    }
    static ObjectNode replay(JsonNode input,ObjectMapper json) throws Exception {
        if(!actualFilterVersion().equals(input.path("filterVersion").asText()))throw new IllegalStateException("Wrong filter classpath");
        var mapper=new WireContract(67_108_864);
        Command command=mapper.decode(json.writeValueAsBytes(input.path("request")));
        var envelope=json.createObjectNode().put("modelVersion",GeminiProtocol.MODEL);
        envelope.putArray("candidates").addObject().put("finishReason","STOP").putObject("content").putArray("parts").addObject().put("text",input.path("structuredProposalText").asText());
        byte[] bytes=json.writeValueAsBytes(envelope);
        var trace=new Capture(json);
        var metadata=new ProviderMetadata(input.path("promptVersion").asText(),input.path("inferenceExecuted").asBoolean()?ExecutionMode.PROVIDER:ExecutionMode.SIMULATED,"google-gemini",input.path("model").asText());
        var service=new ExtractionService(request -> {
            var decoded=new GeminiProtocol().decode(request.clinicalNote(),bytes,trace);
            return new ProviderResponse(decoded.completion(),decoded.proposal(),metadata);
        });
        long filterStart=System.nanoTime();
        Result accepted=service.extract(command,trace);
        double filterMillis=(System.nanoTime()-filterStart)/1_000_000.0;
        if(!accepted.processing().validationVersion().equals(input.path("filterVersion").asText()))throw new IllegalStateException("Wrong filter classpath");
        var out=json.createObjectNode().put("status",200).put("sourceFilterVersion",accepted.processing().validationVersion()).put("filterLatencyMillis",filterMillis);
        out.set("body",mapper.encode(accepted,accepted.requestId()));
        out.putObject("headers").put("X-Request-Id",accepted.requestId().toString()).put("Cache-Control","no-store");
        out.set("trace",trace.snapshot(input.path("inferenceExecuted").asBoolean()));
        var raw=raw(command,trace.proposal,accepted.locale(),metadata);
        out.set("rawBody",mapper.encode(raw,raw.requestId()));
        return out;
    }
    private static Result raw(Command command,Proposal proposal,String locale,ProviderMetadata metadata) {
        var findings=new ArrayList<Accepted>();var paths=new HashMap<Integer,String>();var counts=new EnumMap<Kind,Integer>(Kind.class);
        var resolver=new CatalogResolver(command.catalogs());int source=0;
        for(var mention:proposal.mentions()) {
            int index=counts.getOrDefault(mention.kind(),0);counts.put(mention.kind(),index+1);
            paths.put(source++,"/"+collection(mention.kind())+"/"+index);
            findings.add(new Accepted(mention.kind().name().substring(0,1).toLowerCase(Locale.ROOT)+(index+1),mention,resolver.resolve(mention.kind(),mention.name().value())));
        }
        var warnings=new ArrayList<Warning>();
        for(var notice:proposal.notices())warnings.add(new Warning(notice.code(),notice.targets().stream().map(t -> paths.get(t.mentionIndex())+suffix(t.field())).distinct().toList(),"/clinicalNote"));
        return new Result(UUID.randomUUID(),locale,findings,warnings,new Processing("extraction-core-0.1.0",metadata.promptVersion(),"raw-unvalidated-t8",metadata.executionMode(),"google-gemini",metadata.model()));
    }
    private static String collection(Kind kind){return switch(kind){case DIAGNOSIS->"diagnoses";case MEDICATION->"medications";case PROCEDURE->"procedures";};}
    private static String suffix(Field f){return switch(f){case ENTITY->"";case ASSERTION->"/context/assertion";case SUBJECT->"/context/subject";case TEMPORALITY->"/context/temporality";case PERFORMANCE_STATUS->"/performanceStatus";case PRESCRIPTION_STATUS->"/prescriptionStatus";case MENTIONED_CODE->"/mentionedCode";case DOSAGE->"/dosage";case FREQUENCY->"/frequency";case ROUTE->"/route";case DURATION->"/duration";};}
    private static final class Capture implements ExtractionTrace {
        private final ObjectMapper json;private final List<MentionDecision> decisions=new ArrayList<>();private Proposal proposal;private ProviderRequest request;private String structured;
        Capture(ObjectMapper json){this.json=json;}
        @Override public void started(ProviderRequest value){request=value;}
        @Override public void structuredProposal(String value){structured=value;}
        @Override public void decodedProposal(Proposal value){proposal=value;}
        @Override public void mentionDecision(MentionDecision value){decisions.add(value);}
        ObjectNode snapshot(boolean inference) {
            var node=json.createObjectNode().put("schemaVersion","causal-filter-trace-1").put("syntheticData",true).put("inferenceExecuted",inference).put("structuredProposalText",structured);
            node.set("request",json.valueToTree(request));node.set("decodedProposal",json.valueToTree(proposal));node.set("mentionDecisions",json.valueToTree(decisions));
            for(int i=0;i<proposal.mentions().size();i++)((ObjectNode)node.at("/decodedProposal/mentions/"+i)).put("kind",proposal.mentions().get(i).kind().name());
            return node;
        }
    }
}
