package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.*;
import java.util.*;

/** Additional review regressions; separate from the already frozen 30-case study. */
public final class AssociationReviewCases {
    private AssociationReviewCases() {}
    public static List<Case> all() {
        String lines="Se prescribe Alfa 5 mg\nBeta 9 mg\nfrecuencia: cada 12 horas.";
        String shared="Se prescribe Alfa y Beta, ambos junto a Gamma 9 mg.";
        String triple="Se prescribe Alfa y Beta y Gamma, ambos 9 mg.";
        String code="Alfa 5 mg; código de Beta: X2";
        String route="Alfa 5 mg, por vía oral, durante 3 días.";
        return List.of(
            new Case("review-rejected-code-bridge",lines,new Proposal(List.of(med(lines,"Alfa",0,Map.of("mentionedCode",new Text("Beta 9 mg",List.of(new Evidence(0,1,"bogus"))),"frequency",text(lines,"cada 12 horas",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/frequency",null,"negative"))),
            new Case("review-unassociated-duration-bridge",lines,new Proposal(List.of(med(lines,"Alfa",0,Map.of("duration",text(lines,"Beta 9 mg",0),"frequency",text(lines,"cada 12 horas",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/frequency",null,"negative"))),
            new Case("review-shared-third-head",shared,new Proposal(List.of(med(shared,"Alfa",0,Map.of("dosage",text(shared,"9 mg",0))),med(shared,"Beta",0,Map.of()),med(shared,"Gamma",0,Map.of("dosage",text(shared,"9 mg",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/dosage",null,"negative"),new Expected("/medications/2/dosage","9 mg","positive"))),
            new Case("review-three-coordinated-heads",triple,new Proposal(List.of(med(triple,"Alfa",0,Map.of("dosage",text(triple,"9 mg",0))),med(triple,"Beta",0,Map.of("dosage",text(triple,"9 mg",0))),med(triple,"Gamma",0,Map.of("dosage",text(triple,"9 mg",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/dosage",null,"negative"),new Expected("/medications/1/dosage",null,"negative"),new Expected("/medications/2/dosage",null,"negative"))),
            new Case("review-foreign-code-label",code,new Proposal(List.of(med(code,"Alfa",0,Map.of("mentionedCode",text(code,"X2",0),"dosage",text(code,"5 mg",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/mentionedCode",null,"negative"),new Expected("/medications/0/dosage","5 mg","positive"))),
            new Case("review-common-route-duration",route,new Proposal(List.of(med(route,"Alfa",0,Map.of("dosage",text(route,"5 mg",0),"route",text(route,"oral",0),"duration",text(route,"3 días",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/route","oral","positive"),new Expected("/medications/0/duration","3 días","positive")))
        );
    }
}
