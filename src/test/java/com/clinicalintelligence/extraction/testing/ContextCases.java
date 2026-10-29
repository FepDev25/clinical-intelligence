package com.clinicalintelligence.extraction.testing;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.AssociationCases.text;
import java.util.*;

/** Fixed scripted proposals, with explicit positive, negative and residual field references. */
public final class ContextCases {
    private ContextCases() {}
    public record Expected(String path,String value,String role) {}
    public record Case(String id,String note,Proposal proposal,List<Expected> expected,int findings,boolean contradiction) {}
    static Evidence quote(String n,String q) { return Fixtures.span(n,q); }
    static <T extends Enum<T>> Grounded<T> g(T value,String n,String q) {return new Grounded<>(value,value.name().equals("UNKNOWN")?List.of():List.of(quote(n,q)));}
    static Context context(String n,String q,Assertion a,Temporality t,Subject s) {return new Context(g(a,n,q),g(t,n,q),g(s,n,q));}
    static Diagnosis d(String n,String name,int occurrence,String q,Assertion a,Temporality t,Subject s) {return new Diagnosis(text(n,name,occurrence),null,context(n,q,a,t,s));}
    static Case assertion(String id,String note,String name,Assertion proposed,String expected,String role) {
        return new Case(id,note,new Proposal(List.of(d(note,name,0,note,proposed,Temporality.UNKNOWN,Subject.UNKNOWN)),List.of()),List.of(new Expected("/diagnoses/0/context/assertion/value",expected,role)),1,false);
    }
    static Case diag(String id,String n,String name,String q,Assertion a,Temporality t,Subject s,String dim,String expected,String role) {
        return new Case(id,n,new Proposal(List.of(d(n,name,0,q,a,t,s)),List.of()),List.of(new Expected("/diagnoses/0/context/"+dim+"/value",expected,role)),1,false);
    }
    static Case procedure(String id,String n,Assertion a,Temporality t,PerformanceStatus state,String field,String expected,String role) {
        var p=new Procedure(text(n,"ecografía",0),null,context(n,n,a,t,Subject.UNKNOWN),g(state,n,n));
        return new Case(id,n,new Proposal(List.of(p),List.of()),List.of(new Expected("/procedures/0/"+field+"/value",expected,role)),1,false);
    }
    static Case medication(String id,String n,PrescriptionStatus state,String expected,String role) {
        var m=new Medication(text(n,"Alfa",0),null,context(n,n,Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.UNKNOWN),g(state,n,n),null,null,null,null);
        return new Case(id,n,new Proposal(List.of(m),List.of()),List.of(new Expected("/medications/0/prescriptionStatus/value",expected,role)),1,false);
    }
    public static List<Case> all() {
        var c=new ArrayList<Case>();
        c.add(assertion("negative-negation","El paciente niega asma.","asma",Assertion.AFFIRMED,"unknown","negative"));
        c.add(assertion("positive-negation","El paciente niega asma.","asma",Assertion.NEGATED,"negated","positive"));
        c.add(assertion("positive-affirmation","El paciente tiene asma confirmada.","asma",Assertion.AFFIRMED,"affirmed","positive"));
        c.add(assertion("positive-distant-negation","No presenta fiebre. El paciente tiene asma confirmada.","asma",Assertion.AFFIRMED,"affirmed","positive"));
        c.add(assertion("negative-suspected","Se sospecha asma en el paciente.","asma",Assertion.AFFIRMED,"unknown","negative"));
        c.add(assertion("positive-suspected","Se sospecha asma en el paciente.","asma",Assertion.SUSPECTED,"suspected","positive"));
        c.add(assertion("negative-no-descartar","No se puede descartar asma.","asma",Assertion.NEGATED,"unknown","negative"));
        c.add(assertion("positive-no-descartar","No se puede descartar asma.","asma",Assertion.SUSPECTED,"suspected","positive"));
        c.add(assertion("negative-double-negation","El paciente no niega asma.","asma",Assertion.AFFIRMED,"unknown","negative"));
        c.add(assertion("positive-double-abstention","El paciente no niega asma.","asma",Assertion.UNKNOWN,"unknown","positive"));
        for(boolean valid:List.of(false,true)) {
            String n="El paciente niega asma y rinitis.";
            var a=valid?Assertion.NEGATED:Assertion.AFFIRMED;
            c.add(new Case((valid?"positive":"negative")+"-coordinated-negation",n,new Proposal(List.of(d(n,"asma",0,n,a,Temporality.UNKNOWN,Subject.UNKNOWN),d(n,"rinitis",0,n,a,Temporality.UNKNOWN,Subject.UNKNOWN)),List.of()),List.of(new Expected("/diagnoses/0/context/assertion/value",valid?"negated":"unknown",valid?"positive":"negative"),new Expected("/diagnoses/1/context/assertion/value",valid?"negated":"unknown",valid?"positive":"negative")),2,false));
        }
        c.add(assertion("positive-adversative","El paciente niega fiebre, pero confirma asma.","asma",Assertion.AFFIRMED,"affirmed","positive"));
        c.add(diag("negative-family-as-patient","La madre del paciente tiene asma.","asma","La madre del paciente tiene asma.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.PATIENT,"subject","unknown","negative"));
        c.add(diag("positive-family","La madre del paciente tiene asma.","asma","La madre del paciente tiene asma.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.FAMILY,"subject","family","positive"));
        c.add(diag("positive-patient","El paciente tiene asma.","asma","El paciente tiene asma.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.PATIENT,"subject","patient","positive"));
        c.add(diag("positive-other","El acompañante tiene asma.","asma","El acompañante tiene asma.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.OTHER,"subject","other","positive"));
        String foreign="La madre tiene asma. El paciente recibe Alfa hoy.";
        c.add(diag("negative-foreign-subject-citation",foreign,"asma","paciente",Assertion.UNKNOWN,Temporality.UNKNOWN,Subject.PATIENT,"subject","unknown","negative"));
        c.add(diag("negative-foreign-time-citation",foreign,"asma","hoy",Assertion.UNKNOWN,Temporality.CURRENT,Subject.UNKNOWN,"temporality","unknown","negative"));
        c.add(diag("positive-own-short-subject","El paciente tiene asma.","asma","paciente",Assertion.UNKNOWN,Temporality.UNKNOWN,Subject.PATIENT,"subject","patient","positive"));
        c.add(diag("positive-own-short-time","Hoy se confirma asma.","asma","Hoy",Assertion.UNKNOWN,Temporality.CURRENT,Subject.UNKNOWN,"temporality","current","positive"));
        c.add(diag("negative-historical-as-current","Ayer se confirmó asma en el paciente.","asma","Ayer se confirmó asma en el paciente.",Assertion.AFFIRMED,Temporality.CURRENT,Subject.PATIENT,"temporality","unknown","negative"));
        c.add(diag("positive-historical","Ayer se confirmó asma en el paciente.","asma","Ayer se confirmó asma en el paciente.",Assertion.AFFIRMED,Temporality.HISTORICAL,Subject.PATIENT,"temporality","historical","positive"));
        c.add(procedure("negative-order-today-act-tomorrow","Hoy se solicita ecografía para mañana.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.ORDERED,"context/temporality","unknown","negative"));
        c.add(procedure("positive-order-today-act-tomorrow","Hoy se solicita ecografía para mañana.",Assertion.AFFIRMED,Temporality.FUTURE,PerformanceStatus.ORDERED,"context/temporality","future","positive"));
        c.add(procedure("positive-order-no-date","Se solicita ecografía al paciente.",Assertion.AFFIRMED,Temporality.UNKNOWN,PerformanceStatus.ORDERED,"context/temporality","unknown","positive"));
        c.add(procedure("negative-not-performed-as-performed","No se realizó ecografía hoy.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.PERFORMED,"performanceStatus","unknown","negative"));
        c.add(procedure("positive-not-performed","No se realizó ecografía hoy.",Assertion.NEGATED,Temporality.CURRENT,PerformanceStatus.NOT_PERFORMED,"performanceStatus","not_performed","positive"));
        c.add(procedure("positive-performed-yesterday","Ayer se realizó ecografía al paciente.",Assertion.AFFIRMED,Temporality.HISTORICAL,PerformanceStatus.PERFORMED,"performanceStatus","performed","positive"));
        c.add(procedure("positive-sequence-single-procedure","Se solicitó y se realizó hoy ecografía al paciente.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.PERFORMED,"performanceStatus","performed","positive"));
        c.add(procedure("negative-plan-as-performed","Se planifica ecografía para mañana.",Assertion.AFFIRMED,Temporality.FUTURE,PerformanceStatus.PERFORMED,"performanceStatus","unknown","negative"));
        c.add(procedure("positive-cancelled","Se canceló ecografía hoy.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.CANCELLED,"performanceStatus","cancelled","positive"));
        c.add(medication("negative-suspended-as-prescribed","Hoy se suspende Alfa.",PrescriptionStatus.PRESCRIBED,"unknown","negative"));
        c.add(medication("positive-suspended","Hoy se suspende Alfa.",PrescriptionStatus.SUSPENDED,"suspended","positive"));
        c.add(medication("positive-prescribed","Hoy se prescribe Alfa.",PrescriptionStatus.PRESCRIBED,"prescribed","positive"));
        c.add(medication("positive-reported","El paciente refiere tomar Alfa.",PrescriptionStatus.REPORTED,"reported","positive"));
        c.add(medication("negative-reported-as-prescribed","El paciente refiere tomar Alfa.",PrescriptionStatus.PRESCRIBED,"unknown","negative"));
        // Distinct anchored mentions stay distinct, even for the same literal name.
        for(String mode:List.of("equivalent","sequence","contradiction")) {
            String n=switch(mode) {case "equivalent" -> "Hoy el paciente tiene asma. Hoy el paciente tiene asma.";case "sequence" -> "Al inicio de hoy se sospecha asma; después, hoy se descarta asma en el paciente.";default -> "Registro simultáneo sin resolución: hoy el paciente tiene asma; hoy el paciente niega asma.";};
            var first=mode.equals("sequence")?Assertion.SUSPECTED:Assertion.AFFIRMED;
            var second=mode.equals("equivalent")?Assertion.AFFIRMED:Assertion.NEGATED;
            c.add(new Case("positive-repeat-"+mode,n,new Proposal(List.of(d(n,"asma",0,n,first,Temporality.CURRENT,Subject.UNKNOWN),d(n,"asma",1,n,second,Temporality.CURRENT,Subject.UNKNOWN)),List.of()),List.of(new Expected("/diagnoses/0/context/assertion/value",first.name().toLowerCase(Locale.ROOT),"positive"),new Expected("/diagnoses/1/context/assertion/value",second.name().toLowerCase(Locale.ROOT),"positive")),2,mode.equals("contradiction")));
        }
        // Explicit ellipsis: the antecedent quote plus the continuing clause is admissible.
        String ellipsis="La paciente consulta, se le confirma asma.";
        var subj=new Grounded<>(Subject.PATIENT,List.of(quote(ellipsis,"La paciente"),quote(ellipsis,"se le confirma asma")));
        c.add(new Case("positive-explicit-ellipsis",ellipsis,new Proposal(List.of(new Diagnosis(text(ellipsis,"asma",0),null,new Context(g(Assertion.AFFIRMED,ellipsis,"se le confirma asma"),g(Temporality.UNKNOWN,ellipsis,ellipsis),subj))),List.of()),List.of(new Expected("/diagnoses/0/context/subject/value","patient","positive")),1,false));
        String unicode="🩺 El paciente niega A\u0301sma.\r\nHoy recibe Alfa.";
        c.add(assertion("negative-unicode-crlf",unicode,"A\u0301sma",Assertion.AFFIRMED,"unknown","negative"));
        c.add(assertion("residual-opaque-negation","La presencia de asma queda lejos de estar demostrada.","asma",Assertion.AFFIRMED,"unknown","residual"));
        return List.copyOf(c);
    }
}
