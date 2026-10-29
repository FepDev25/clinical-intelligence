package com.clinicalintelligence.extraction.application;
import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.text;
import com.clinicalintelligence.extraction.testing.Fixtures;
import org.junit.jupiter.api.Test;
import java.util.*;

class AssociationUnicodeTest {
    private Medication medication(String note,Text dosage,Text route,Text duration) {
        var whole=new Evidence(0,note.codePointCount(0,note.length()),note);
        var context=new Context(new Grounded<>(Assertion.AFFIRMED,List.of(whole)),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
        return new Medication(text(note,"A\u0301lfa",0),null,context,new Grounded<>(PrescriptionStatus.PRESCRIBED,List.of(whole)),dosage,null,route,duration);
    }
    @Test void nonBreakingSpaceDoesNotHideSentenceBoundary() {
        String note="🩺 A\u0301lfa 5 mg.\u00a0Beta 9 mg.";
        var proposed=medication(note,text(note,"9 mg",0),null,null);
        var result=new ExtractionService(ignored -> Fixtures.completed(new Proposal(List.of(proposed),List.of()))).extract(Command.note(note));
        assertNull(((Medication)result.medications().getFirst().mention()).dosage());
    }
    @Test void decomposedLabelsAndCrlfDoNotChangeLiteralOffsets() {
        String note="🩺 A\u0301lfa:\r\nvi\u0301a: oral\r\nduracio\u0301n: 3 di\u0301as.";
        var route=text(note,"oral",0);var duration=text(note,"3 di\u0301as",0);
        var proposed=medication(note,null,route,duration);
        var result=new ExtractionService(ignored -> Fixtures.completed(new Proposal(List.of(proposed),List.of()))).extract(Command.note(note));
        var accepted=(Medication)result.medications().getFirst().mention();
        assertEquals(route,accepted.route());assertEquals(duration,accepted.duration());
        assertTrue(result.warnings().isEmpty());
    }
    @Test void overlappingCitationsKeepOriginalCodePointAnchors() {
        String note="🩺 A\u0301lfa A\u0301lfa A\u0301lfa, código X1.\r\n";
        var first=text(note,"A\u0301lfa A\u0301lfa",0).evidence().getFirst();
        var second=text(note,"A\u0301lfa A\u0301lfa",1).evidence().getFirst();
        var name=new Text("A\u0301lfa",List.of(first,second));
        var context=new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
        var mention=new Diagnosis(name,text(note,"X1",0),context);
        var result=new ExtractionService(ignored -> Fixtures.completed(new Proposal(List.of(mention),List.of()))).extract(Command.note(note));
        assertEquals(List.of(first,second),result.diagnoses().getFirst().mention().name().evidence());
        assertEquals("X1",result.diagnoses().getFirst().mention().mentionedCode().value());
        assertTrue(new EvidencePolicy().validText(note,result.diagnoses().getFirst().mention().name()));
        assertTrue(result.warnings().isEmpty());
    }
}
