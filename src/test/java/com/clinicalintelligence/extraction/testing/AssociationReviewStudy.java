package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.*;
import com.clinicalintelligence.http.WireContract;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Run with old OR new application classes first in classpath. Local scripted proposals only. */
public final class AssociationReviewStudy {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("One new output path required");
        var json=new ObjectMapper();var report=json.createObjectNode();
        report.put("version","association-review-study-1").put("inferenceExecuted",false).put("scope","local scripted field-regression study; not clinical or model validation");
        var rows=report.putArray("cases");
        for(var fixture:AssociationReviewCases.all()) {
            var decisions=new ArrayList<ExtractionTrace.MentionDecision>();
            var trace=new ExtractionTrace() { @Override public void mentionDecision(MentionDecision d) { decisions.add(d); } };
            var result=new ExtractionService(ignored -> Fixtures.completed(fixture.proposal())).extract(new Command(fixture.note(),"es",fixture.catalogs()),trace);
            var body=new WireContract(1_000_000).encode(result,result.requestId());
            var row=rows.addObject().put("id",fixture.id()).put("note",fixture.note());
            row.set("proposal",json.valueToTree(fixture.proposal()));row.set("catalogs",json.valueToTree(fixture.catalogs()));
            row.set("expected",json.valueToTree(fixture.expected()));row.set("response",body);row.set("decisions",json.valueToTree(decisions));
        }
        Files.writeString(Path.of(args[0]),json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n",StandardOpenOption.CREATE_NEW);
    }
}
