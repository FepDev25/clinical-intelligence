package com.clinicalintelligence.extraction.application;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static org.junit.jupiter.api.Assertions.*;
import com.clinicalintelligence.extraction.testing.*;
import com.clinicalintelligence.http.WireContract;
import org.junit.jupiter.api.*;
import java.util.stream.Stream;
class ContextPolicyTest {
    @TestFactory Stream<DynamicTest> contextPairs() {
        return Stream.concat(ContextCases.all().stream(),ContextReviewCases.all().stream()).filter(f -> f.expected().stream().noneMatch(e -> e.role().equals("residual"))).map(f -> DynamicTest.dynamicTest(f.id(),() -> {
            var result=new ExtractionService(ignored -> Fixtures.completed(f.proposal())).extract(new Command(f.note(),"es",java.util.List.of()));
            var body=new WireContract(1_000_000).encode(result,result.requestId());
            for(var e:f.expected())assertEquals(e.value(),body.at(e.path()).asText(),e.path());
            assertEquals(f.findings(),result.findings().size());
            assertEquals(f.contradiction(),result.warnings().stream().anyMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
            for(var e:f.expected())if(e.role().equals("negative")) {
                assertTrue(body.at(e.path().replace("/value","/evidence")).isEmpty());
                assertTrue(result.warnings().stream().anyMatch(w -> w.code()==WarningCode.ATTRIBUTE_DISCARDED && w.targets().contains(e.path().replace("/value",""))));
            }
        }));
    }
}
