package com.clinicalintelligence.extraction.application;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.text;
import static com.clinicalintelligence.extraction.testing.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class ContextRelationsTest {
    static Context ctx(String n,Assertion a) {return new Context(grounded(a,n,n),grounded(Temporality.CURRENT,n,n),new Grounded<>(Subject.UNKNOWN,List.of()));}
    static Result pair(String note) {
        var a=new Diagnosis(text(note,"asma",0),null,ctx(note,Assertion.AFFIRMED));
        var b=new Diagnosis(text(note,"asma",1),null,ctx(note,Assertion.NEGATED));
        return new ExtractionService(ignored -> completed(new Proposal(List.of(a,b),List.of()))).extract(Command.note(note));
    }
    @Test void unrelatedSimultaneousMarkerCannotCreateAContradiction() {
        String n="Registro simultáneo sin resolución: otro asunto.\n\nHoy el paciente tiene asma; hoy el paciente niega asma.";
        assertTrue(pair(n).warnings().stream().noneMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
    }
    @Test void simultaneousMarkerHasToPrecedeTheMentions() {
        String n="Hoy el paciente tiene asma; hoy el paciente niega asma. Registro simultáneo sin resolución: otro asunto.";
        assertTrue(pair(n).warnings().stream().noneMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
    }
    @Test void simultaneousMentionsGetOneResolvableWarningAndRemainDistinct() {
        String n="Registro simultáneo sin resolución: hoy el paciente tiene asma; hoy el paciente niega asma.";
        var r=pair(n);assertEquals(2,r.diagnoses().size());
        var w=r.warnings().stream().filter(v -> v.code()==WarningCode.CONTRADICTORY_MENTIONS).toList();
        assertEquals(1,w.size());assertEquals(List.of("/diagnoses/0","/diagnoses/1"),w.getFirst().targets());
    }
    @Test void mergedIncompatibleOccurrencesAbstainInsteadOfChoosingOne() {
        String n="Hoy el paciente tiene asma. Hoy el paciente niega asma.";
        var name=new Text("asma",List.of(text(n,"asma",0).evidence().getFirst(),text(n,"asma",1).evidence().getFirst()));
        var a=new Diagnosis(name,null,ctx(n,Assertion.AFFIRMED));
        var r=new ExtractionService(ignored -> completed(new Proposal(List.of(a),List.of()))).extract(Command.note(n));
        assertEquals(Assertion.UNKNOWN,r.diagnoses().getFirst().mention().context().assertion().value());
        assertEquals(2,r.diagnoses().getFirst().mention().name().evidence().size());
    }
    @Test void sharedNameCannotCreateConflictAcrossFamilyAndPatient() {
        String n="Registro simultáneo sin resolución: hoy la madre tiene asma; hoy el paciente niega asma.";
        assertTrue(pair(n).warnings().stream().noneMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
    }
    @Test void familyEnumDoesNotMakeMotherAndFatherTheSamePerson() {
        String n="Registro simultáneo sin resolución: hoy la madre tiene asma; hoy el padre niega asma.";
        assertTrue(pair(n).warnings().stream().noneMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
    }
    @Test void patientEnumDoesNotMakeTwoNamedPatientsTheSamePerson() {
        String n="Registro simultáneo sin resolución: hoy el paciente A tiene asma; hoy el paciente B niega asma.";
        assertTrue(pair(n).warnings().stream().noneMatch(w -> w.code()==WarningCode.CONTRADICTORY_MENTIONS));
    }
    @Test void negatedSuspicionDoesNotSupportSuspected() {
        String n="No se sospecha asma.";
        var d=new Diagnosis(text(n,"asma",0),null,ctx(n,Assertion.SUSPECTED));
        var r=new ExtractionService(ignored -> completed(new Proposal(List.of(d),List.of()))).extract(Command.note(n));
        assertEquals(Assertion.UNKNOWN,r.diagnoses().getFirst().mention().context().assertion().value());
    }
    @Test void equivalentGroupedOccurrencesKeepAllTheirAnchors() {
        String n="Hoy el paciente tiene asma. Hoy el paciente tiene asma.";
        var name=new Text("asma",List.of(text(n,"asma",0).evidence().getFirst(),text(n,"asma",1).evidence().getFirst()));
        var d=new Diagnosis(name,null,ctx(n,Assertion.AFFIRMED));
        var r=new ExtractionService(ignored -> completed(new Proposal(List.of(d),List.of()))).extract(Command.note(n));
        assertEquals(d,r.diagnoses().getFirst().mention());assertEquals(1,r.diagnoses().size());assertTrue(r.warnings().isEmpty());
    }
}
