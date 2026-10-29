package com.clinicalintelligence.extraction.application;

import static org.junit.jupiter.api.Assertions.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.text;
import com.clinicalintelligence.extraction.testing.Fixtures;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssociationReviewTest {
    private Medication med(String note,String name,Text code,Text dose,Text frequency,Text route,Text duration) {
        var scope=new Evidence(0,note.codePointCount(0,note.length()),note);
        var context=new Context(new Grounded<>(Assertion.AFFIRMED,List.of(scope)),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
        return new Medication(text(note,name,0),code,context,new Grounded<>(PrescriptionStatus.PRESCRIBED,List.of(scope)),dose,frequency,route,duration);
    }
    private Result run(String note,Mention... mentions) {return new ExtractionService(ignored -> Fixtures.completed(new Proposal(List.of(mentions),List.of()))).extract(Command.note(note));}
    @Test void rejectedCodeCannotBridgeTheOtherPrescription() {
        String note="Se prescribe Alfa 5 mg\nBeta 9 mg\nfrecuencia: cada 12 horas.";
        var frequency=text(note,"cada 12 horas",0);
        var without=run(note,med(note,"Alfa",null,null,frequency,null,null));
        var poisoned=run(note,med(note,"Alfa",new Text("Beta 9 mg",List.of(new Evidence(0,1,"bogus"))),null,frequency,null,null));
        assertNull(((Medication)without.medications().getFirst().mention()).frequency());
        assertNull(((Medication)poisoned.medications().getFirst().mention()).mentionedCode());
        assertNull(((Medication)poisoned.medications().getFirst().mention()).frequency());
    }
    @Test void rejectedOrValidButUnassociatedAttributeCannotBeUsedAsBridge() {
        String note="Se prescribe Alfa 5 mg\nBeta 9 mg\nfrecuencia: cada 12 horas.";
        var result=run(note,med(note,"Alfa",null,null,text(note,"cada 12 horas",0),null,text(note,"Beta 9 mg",0)));
        assertNull(((Medication)result.medications().getFirst().mention()).frequency());
        assertNull(((Medication)result.medications().getFirst().mention()).duration());
    }
    @Test void sharedMarkerCannotCrossAKnownThirdMedication() {
        String note="Se prescribe Alfa y Beta, ambos junto a Gamma 9 mg.";
        var result=run(note,med(note,"Alfa",null,text(note,"9 mg",0),null,null,null),med(note,"Beta",null,null,null,null,null),med(note,"Gamma",null,text(note,"9 mg",0),null,null,null));
        assertNull(((Medication)result.medications().get(0).mention()).dosage());
        assertEquals("9 mg",((Medication)result.medications().get(2).mention()).dosage().value());
    }
    @Test void threeCoordinatedHeadsCannotInventWhichPairAmbosRefersTo() {
        String note="Se prescribe Alfa y Beta y Gamma, ambos 9 mg.";
        var result=run(note,med(note,"Alfa",null,text(note,"9 mg",0),null,null,null),med(note,"Beta",null,text(note,"9 mg",0),null,null,null),med(note,"Gamma",null,text(note,"9 mg",0),null,null,null));
        for(var accepted:result.medications())assertNull(((Medication)accepted.mention()).dosage());
    }
    @Test void namedForeignCodeCannotUseAGenericCodeLabelAsBridge() {
        String note="Alfa 5 mg; código de Beta: X2";
        var result=run(note,med(note,"Alfa",text(note,"X2",0),text(note,"5 mg",0),null,null,null));
        assertNull(result.medications().getFirst().mention().mentionedCode());
        assertEquals("5 mg",((Medication)result.medications().getFirst().mention()).dosage().value());
    }
    @Test void commonRouteAndDurationIntroductionsRemainSupported() {
        String note="Alfa 5 mg, por vía oral, durante 3 días.";
        var result=run(note,med(note,"Alfa",null,text(note,"5 mg",0),null,text(note,"oral",0),text(note,"3 días",0)));
        var accepted=(Medication)result.medications().getFirst().mention();
        assertEquals("oral",accepted.route().value());assertEquals("3 días",accepted.duration().value());
        assertTrue(result.warnings().isEmpty());
    }
}
