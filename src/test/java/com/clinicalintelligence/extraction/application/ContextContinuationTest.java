package com.clinicalintelligence.extraction.application;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.text;
import static com.clinicalintelligence.extraction.testing.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ContextContinuationTest {
    private Diagnosis run(String note,String antecedent,String local,Subject proposed) {
        var subject=new Grounded<>(proposed,List.of(span(note,antecedent),span(note,local)));
        var d=new Diagnosis(text(note,"asma",0),null,new Context(new Grounded<>(Assertion.UNKNOWN,List.of()),new Grounded<>(Temporality.UNKNOWN,List.of()),subject));
        return (Diagnosis)new ExtractionService(ignored -> completed(new Proposal(List.of(d),List.of()))).extract(Command.note(note)).diagnoses().getFirst().mention();
    }
    @Test void explicitSectionAntecedentAndOwnClauseAreRetained() {
        String n="Paciente valorado hoy. Se documenta asma.";
        assertEquals(Subject.PATIENT,run(n,"Paciente valorado hoy.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void explicitAntecedentDoesNotCrossSubjectChange() {
        String n="Paciente valorado hoy. La madre consulta. Se documenta asma.";
        assertEquals(Subject.UNKNOWN,run(n,"Paciente valorado hoy.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void explicitAntecedentDoesNotCrossBlankParagraph() {
        String n="Paciente valorado hoy.\r\n\r\nSe documenta asma.";
        assertEquals(Subject.UNKNOWN,run(n,"Paciente valorado hoy.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void wideCitationCannotHideSubjectChangeInsideIt() {
        String n="Paciente valorado. La madre consulta. Se documenta asma.";
        assertEquals(Subject.UNKNOWN,run(n,"Paciente valorado. La madre consulta.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void wideCitationCannotHideBlankParagraphInsideIt() {
        String n="Paciente valorado.\r\n\r\nSigue nota. Se documenta asma.";
        assertEquals(Subject.UNKNOWN,run(n,"Paciente valorado.\r\n\r\nSigue nota.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void aSingleCrlfDoesNotActLikeBlankParagraph() {
        String n="Paciente valorado.\r\nSe documenta asma.";
        assertEquals(Subject.PATIENT,run(n,"Paciente valorado.","Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void wholeNoteCitationCannotHideSubjectChangeEither() {
        String n="Paciente valorado. La madre consulta. Se documenta asma.";
        assertEquals(Subject.UNKNOWN,run(n,n,"Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void wholeNoteCannotBorrowARetrospectiveSubject() {
        String n="Se documenta asma. El paciente consulta por otra causa.";
        assertEquals(Subject.UNKNOWN,run(n,n,"Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void wholeNoteCanCiteAnExplicitForwardAntecedent() {
        String n="Paciente valorado. Se documenta asma.";
        assertEquals(Subject.PATIENT,run(n,n,"Se documenta asma.",Subject.PATIENT).context().subject().value());
    }
    @Test void anExplicitNonRelativeAppositionDoesNotChangeTheSubject() {
        String n="Su acompañante, sin parentesco con el paciente, relata asma.";
        assertEquals(Subject.OTHER,run(n,n,"relata asma.",Subject.OTHER).context().subject().value());
    }
    @Test void mentioningPatientInsideNonRelativeAppositionDoesNotMakeItPatient() {
        String n="Su acompañante, sin parentesco con el paciente, relata asma.";
        assertEquals(Subject.UNKNOWN,run(n,n,"relata asma.",Subject.PATIENT).context().subject().value());
    }
}
