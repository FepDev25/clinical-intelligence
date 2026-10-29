package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.util.*;

/** Versioned, hand-annotated synthetic field references; no provider quality claims. */
public final class AssociationCases {
    private AssociationCases() {}
    public record Expected(String path, String value, String role) {}
    public record Case(String id, String note, Proposal proposal, List<Catalog> catalogs, List<Expected> expected) {}
    public static Text text(String note,String value,int occurrence) {
        int at=-1,from=0;
        for(int i=0;i<=occurrence;i++) { at=note.indexOf(value,from);if(at<0)throw new IllegalArgumentException("Missing fixture occurrence");from=at+1; }
        int start=note.codePointCount(0,at);
        return new Text(value,List.of(new Evidence(start,start+value.codePointCount(0,value.length()),value)));
    }
    static Evidence whole(String note) {return new Evidence(0,note.codePointCount(0,note.length()),note);}
    static Context context(String note) {return new Context(new Grounded<>(Assertion.AFFIRMED,List.of(whole(note))),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));}
    static Medication med(String note,String name,int occurrence,Map<String,Text> values) {
        return new Medication(text(note,name,occurrence),values.get("mentionedCode"),context(note),new Grounded<>(PrescriptionStatus.PRESCRIBED,List.of(whole(note))),
                values.get("dosage"),values.get("frequency"),values.get("route"),values.get("duration"));
    }
    static Diagnosis diagnosis(String note,String name,String code) {return new Diagnosis(text(note,name,0),text(note,code,0),context(note));}
    static Case one(String id,String note,String name,int occurrence,String field,String value,int valueOccurrence,String expected,String role) {
        return new Case(id,note,new Proposal(List.of(med(note,name,occurrence,Map.of(field,text(note,value,valueOccurrence)))),List.of()),List.of(),List.of(new Expected("/medications/0/"+field,expected,role)));
    }
    public static List<Case> all() {
        var cases=new ArrayList<Case>();
        cases.add(one("positive-simple","Se prescribe Alfa 5 mg.","Alfa",0,"dosage","5 mg",0,"5 mg","positive"));
        cases.add(one("positive-multiline-label","Se prescribe Alfa:\ndosis: 5 mg\nfrecuencia: cada 8 horas.","Alfa",0,"dosage","5 mg",0,"5 mg","positive"));
        String lines="Se prescribe Alfa:\n5 mg\ncada 8 horas\noral\ndurante 3 días";
        cases.add(new Case("positive-multiline-values",lines,new Proposal(List.of(med(lines,"Alfa",0,Map.of("dosage",text(lines,"5 mg",0),"frequency",text(lines,"cada 8 horas",0),"route",text(lines,"oral",0),"duration",text(lines,"durante 3 días",0)))),List.of()),List.of(),
                List.of(new Expected("/medications/0/dosage","5 mg","positive"),new Expected("/medications/0/frequency","cada 8 horas","positive"),new Expected("/medications/0/route","oral","positive"),new Expected("/medications/0/duration","durante 3 días","positive"))));
        cases.add(one("positive-pronoun","Se prescribe Alfa. Su dosis es 5 mg.","Alfa",0,"dosage","5 mg",0,"5 mg","positive"));
        String equal="Se prescribe Alfa 5 mg. Se prescribe Beta 5 mg.";
        cases.add(new Case("positive-equal-doses",equal,new Proposal(List.of(med(equal,"Alfa",0,Map.of("dosage",text(equal,"5 mg",0))),med(equal,"Beta",0,Map.of("dosage",text(equal,"5 mg",1)))),List.of()),List.of(),
                List.of(new Expected("/medications/0/dosage","5 mg","positive"),new Expected("/medications/1/dosage","5 mg","positive"))));
        String shared="Se prescribe Alfa y Beta, ambos cada 8 horas.";
        cases.add(new Case("positive-shared-frequency",shared,new Proposal(List.of(med(shared,"Alfa",0,Map.of("frequency",text(shared,"cada 8 horas",0))),med(shared,"Beta",0,Map.of("frequency",text(shared,"cada 8 horas",0)))),List.of()),List.of(),
                List.of(new Expected("/medications/0/frequency","cada 8 horas","positive"),new Expected("/medications/1/frequency","cada 8 horas","positive"))));
        cases.add(one("positive-second-occurrence","Alfa: se prescribe 5 mg. Alfa: se reporta 9 mg.","Alfa",1,"dosage","9 mg",0,"9 mg","positive"));
        String decimals="Se prescribe Alfa 1,5 mg por v.o. cada 8 horas.";
        cases.add(new Case("positive-decimals-abbreviation",decimals,new Proposal(List.of(med(decimals,"Alfa",0,Map.of("dosage",text(decimals,"1,5 mg",0),"frequency",text(decimals,"cada 8 horas",0)))),List.of()),List.of(),
                List.of(new Expected("/medications/0/dosage","1,5 mg","positive"),new Expected("/medications/0/frequency","cada 8 horas","positive"))));
        String dx="Diagnóstico asma con código J45.9.";
        cases.add(new Case("positive-diagnosis-code",dx,new Proposal(List.of(diagnosis(dx,"asma","J45.9")),List.of()),List.of(),List.of(new Expected("/diagnoses/0/mentionedCode","J45.9","positive"))));
        String prefix="J45.9 — asma.";
        cases.add(new Case("positive-prefix-code",prefix,new Proposal(List.of(diagnosis(prefix,"asma","J45.9")),List.of()),List.of(),List.of(new Expected("/diagnoses/0/mentionedCode","J45.9","positive"))));
        String proc="Se documenta electrocardiograma, código 93000.";
        cases.add(new Case("positive-procedure-code",proc,new Proposal(List.of(new Procedure(text(proc,"electrocardiograma",0),text(proc,"93000",0),context(proc),new Grounded<>(PerformanceStatus.UNKNOWN,List.of()))),List.of()),List.of(),List.of(new Expected("/procedures/0/mentionedCode","93000","positive"))));
        String catalog="Diagnóstico asma, código externo X1.";
        cases.add(new Case("positive-catalog-independent",catalog,new Proposal(List.of(diagnosis(catalog,"asma","X1")),List.of()),List.of(new Catalog("local","v1",Kind.DIAGNOSIS,List.of(new CatalogEntry("internal-entry","asma",List.of())))),List.of(new Expected("/diagnoses/0/mentionedCode","X1","positive"))));
        cases.add(one("positive-package-vs-dose","Envase Alfa 10 mg; hoy se prescribe Alfa media tableta.","Alfa",1,"dosage","media tableta",0,"media tableta","positive"));
        cases.add(one("positive-own-dose-wide-scope","Se prescribe Alfa 5 mg. Se prescribe Beta 9 mg.","Alfa",0,"dosage","5 mg",0,"5 mg","positive"));
        cases.add(one("positive-unicode-crlf","🩺 Se prescribe A\u0301lfa:\r\ndosis: 5 mg\r\nfrecuencia: cada 8 horas.","A\u0301lfa",0,"dosage","5 mg",0,"5 mg","positive"));
        String compound="Diagnóstico dolor de cabeza y cuello, código X2.";
        cases.add(new Case("positive-compound-name",compound,new Proposal(List.of(diagnosis(compound,"dolor de cabeza y cuello","X2")),List.of()),List.of(),List.of(new Expected("/diagnoses/0/mentionedCode","X2","positive"))));
        cases.add(one("negative-other-dose","Se prescribe Alfa 5 mg. Se prescribe Beta 9 mg.","Alfa",0,"dosage","9 mg",0,null,"negative"));
        cases.add(one("negative-other-code","Se prescribe Alfa. Código de otro medicamento Beta: J02.","Alfa",0,"mentionedCode","J02",0,null,"negative"));
        for(String separator:List.of("; ",", "," y ","\n")) {
            String id=switch(separator) {case "; " -> "semicolon";case ", " -> "comma";case " y " -> "coordination";default -> "newline";};
            cases.add(one("negative-dose-"+id,"Se prescribe Alfa 5 mg"+separator+"Beta 9 mg.","Alfa",0,"dosage","9 mg",0,null,"negative"));
        }
        cases.add(one("negative-package-as-dose","Envase Alfa 10 mg; hoy se prescribe Alfa media tableta.","Alfa",1,"dosage","10 mg",0,null,"negative"));
        cases.add(one("negative-control-duration","Se prescribe Alfa 5 mg. Control en 7 días.","Alfa",0,"duration","7 días",0,null,"negative"));
        String mixed="Se prescribe Alfa 5 mg. Se prescribe Beta 5 mg.";
        cases.add(new Case("negative-wide-repeated-value",mixed,new Proposal(List.of(med(mixed,"Alfa",0,Map.of("dosage",new Text("5 mg",List.of(whole(mixed)))))),List.of()),List.of(),List.of(new Expected("/medications/0/dosage",null,"negative"))));
        cases.add(one("negative-repeated-owner","Alfa: 5 mg. Alfa: 9 mg.","Alfa",0,"dosage","9 mg",0,null,"negative"));
        String same="asma J45 diabetes E11";
        cases.add(new Case("negative-known-other-head",same,new Proposal(List.of(diagnosis(same,"asma","E11"),diagnosis(same,"diabetes","E11")),List.of()),List.of(),List.of(new Expected("/diagnoses/0/mentionedCode",null,"negative"),new Expected("/diagnoses/1/mentionedCode","E11","positive"))));
        cases.add(new Case("negative-borrowed-prefix",same,new Proposal(List.of(diagnosis(same,"asma","J45"),diagnosis(same,"diabetes","J45")),List.of()),List.of(),List.of(new Expected("/diagnoses/0/mentionedCode","J45","positive"),new Expected("/diagnoses/1/mentionedCode",null,"negative"))));
        String ambiguous="Se prescribe Alfa y Beta. Su dosis es 5 mg.";
        cases.add(new Case("negative-ambiguous-pronoun",ambiguous,new Proposal(List.of(med(ambiguous,"Alfa",0,Map.of("dosage",text(ambiguous,"5 mg",0))),med(ambiguous,"Beta",0,Map.of("dosage",text(ambiguous,"5 mg",0)))),List.of()),List.of(),List.of(new Expected("/medications/0/dosage",null,"negative"),new Expected("/medications/1/dosage",null,"negative"))));
        cases.add(one("residual-unlisted-no-separator","Se prescribe Alfa 5 mg Beta 9 mg","Alfa",0,"dosage","9 mg",0,null,"residual"));
        return List.copyOf(cases);
    }
}
