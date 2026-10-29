package com.clinicalintelligence.extraction.testing;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.*;
import com.clinicalintelligence.http.WireContract;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
/** Actual T5/T6 classpaths; no inference. Also exports the entire unchanged development revision. */
public final class ContextReviewStudy {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("One new output path required");
        var json=new ObjectMapper();var report=json.createObjectNode();
        report.put("version","context-study-1").put("inferenceExecuted",false);var rows=report.putArray("cases");
        for(var f:ContextReviewCases.all()) {
            var decisions=new ArrayList<ExtractionTrace.MentionDecision>();
            var trace=new ExtractionTrace(){@Override public void mentionDecision(MentionDecision d){decisions.add(d);}};
            var result=new ExtractionService(ignored -> Fixtures.completed(f.proposal())).extract(new Command(f.note(),"es",List.of()),trace);
            var r=rows.addObject().put("id",f.id()).put("note",f.note());
            r.set("proposal",json.valueToTree(f.proposal()));r.set("expected",json.valueToTree(f.expected()));r.put("expectedFindings",f.findings()).put("expectedContradiction",f.contradiction());
            r.set("response",new WireContract(1_000_000).encode(result,result.requestId()));r.set("decisions",json.valueToTree(decisions));
        }
        Files.writeString(Path.of(args[0]),json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n",StandardOpenOption.CREATE_NEW);
    }
}
