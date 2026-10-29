package com.clinicalintelligence.extraction.application;

import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.testing.AssociationCases;
import com.clinicalintelligence.extraction.testing.Fixtures;
import com.clinicalintelligence.http.WireContract;
import org.junit.jupiter.api.Test;

class AssociationPolicyTest {
    @org.junit.jupiter.api.TestFactory java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> controlledAssociations() {
        return AssociationCases.all().stream().filter(f -> f.expected().stream().anyMatch(e -> !e.role().equals("residual"))).map(fixture -> org.junit.jupiter.api.DynamicTest.dynamicTest(fixture.id(), () -> {
            var service=new ExtractionService(ignored -> Fixtures.completed(fixture.proposal()));
            var result=service.extract(new Command(fixture.note(),"es",fixture.catalogs()));
            var body=new WireContract(1_000_000).encode(result,result.requestId());
            for(var expected:fixture.expected()) {
                if(expected.role().equals("residual"))continue;
                var actual=body.at(expected.path());
                if(expected.value()==null) {
                    assertTrue(actual.isNull(),fixture.id()+" "+expected.path()+" must be removed");
                    assertTrue(result.warnings().stream().anyMatch(w -> w.code()==WarningCode.ATTRIBUTE_DISCARDED && w.targets().contains(expected.path())),fixture.id());
                } else assertEquals(expected.value(),actual.path("value").asText(),fixture.id()+" "+expected.path());
            }
        }));
    }
    @Test void catalogStillResolvesByNameAndDoesNotInventMentionedCode() {
        var fixture=AssociationCases.all().stream().filter(c -> c.id().equals("positive-catalog-independent")).findFirst().orElseThrow();
        var result=new ExtractionService(ignored -> Fixtures.completed(fixture.proposal())).extract(new Command(fixture.note(),"es",fixture.catalogs()));
        assertEquals(MatchStatus.MATCHED,result.diagnoses().getFirst().catalogMatch().status());
        assertEquals("internal-entry",result.diagnoses().getFirst().catalogMatch().candidates().getFirst().entryId());
        assertEquals("X1",result.diagnoses().getFirst().mention().mentionedCode().value());
    }
}
