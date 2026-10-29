package com.clinicalintelligence.extraction.testing;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import static com.clinicalintelligence.extraction.testing.ContextCases.*;
import java.util.*;
/** Supplementary development controls, kept separate from the fixed initial study. */
public final class ContextReviewCases {
    private ContextReviewCases() {}
    public static List<ContextCases.Case> all() {
        var c=new ArrayList<ContextCases.Case>();
        c.add(medication("negative-no-prescription","No se prescribe Alfa.",PrescriptionStatus.PRESCRIBED,"unknown","negative"));
        c.add(procedure("negative-no-order","No se solicita ecografía.",Assertion.NEGATED,Temporality.UNKNOWN,PerformanceStatus.ORDERED,"performanceStatus","unknown","negative"));
        c.add(procedure("negative-no-cancellation","No se canceló ecografía.",Assertion.NEGATED,Temporality.UNKNOWN,PerformanceStatus.CANCELLED,"performanceStatus","unknown","negative"));
        c.add(assertion("negative-no-confirmation","No se confirma asma.","asma",Assertion.AFFIRMED,"unknown","negative"));
        c.add(procedure("negative-interposed-time","No se realizó hoy ecografía.",Assertion.NEGATED,Temporality.CURRENT,PerformanceStatus.PERFORMED,"performanceStatus","unknown","negative"));
        c.add(procedure("positive-interposed-time","Se realizó hoy ecografía.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.PERFORMED,"performanceStatus","performed","positive"));
        c.add(procedure("negative-postposed-status","ecografía: realizada hoy.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.ORDERED,"performanceStatus","unknown","negative"));
        c.add(procedure("positive-postposed-status","ecografía: realizada hoy.",Assertion.AFFIRMED,Temporality.CURRENT,PerformanceStatus.PERFORMED,"performanceStatus","performed","positive"));
        c.add(diag("negative-coordinated-subject","La madre tiene asma y el paciente tiene rinitis.","asma","La madre tiene asma y el paciente tiene rinitis.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.PATIENT,"subject","unknown","negative"));
        c.add(diag("positive-coordinated-subject","La madre tiene asma y el paciente tiene rinitis.","asma","La madre tiene asma y el paciente tiene rinitis.",Assertion.AFFIRMED,Temporality.UNKNOWN,Subject.FAMILY,"subject","family","positive"));
        c.add(diag("negative-coordinated-time","Hoy se confirma asma y ayer se confirmó rinitis.","asma","Hoy se confirma asma y ayer se confirmó rinitis.",Assertion.AFFIRMED,Temporality.HISTORICAL,Subject.UNKNOWN,"temporality","unknown","negative"));
        c.add(diag("positive-coordinated-time","Hoy se confirma asma y ayer se confirmó rinitis.","asma","Hoy se confirma asma y ayer se confirmó rinitis.",Assertion.AFFIRMED,Temporality.CURRENT,Subject.UNKNOWN,"temporality","current","positive"));
        return List.copyOf(c);
    }
}
