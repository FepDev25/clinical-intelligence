package com.clinicalintelligence.extraction.infrastructure.gemini;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.EvidencePolicy;
import java.util.*;
/** Structural boundary only, never decides whether a clinical classification is correct. */
final class GeminiStageBoundary {
    private record Anchor(Kind kind,String value,Evidence evidence) {}
    static void detection(String note,Proposal proposal) {
        if(proposal==null || proposal.mentions()==null || proposal.notices()==null || !proposal.notices().isEmpty())invalid();
        var seen=new HashSet<Anchor>();var evidence=new EvidencePolicy();
        for(var m:proposal.mentions()) {
            if(m==null || !evidence.validText(note,m.name()) || m.name().evidence().size()!=1 || m.mentionedCode()!=null || m.context()==null)invalid();
            if(!unknown(m.context().assertion()) || !unknown(m.context().subject()) || !unknown(m.context().temporality()))invalid();
            if(m instanceof Medication med && (!unknown(med.prescriptionStatus()) || med.dosage()!=null || med.frequency()!=null || med.route()!=null || med.duration()!=null))invalid();
            if(m instanceof Procedure proc && !unknown(proc.performanceStatus()))invalid();
            for(var span:m.name().evidence())if(!span.quote().equals(m.name().value()) || !seen.add(new Anchor(m.kind(),m.name().value(),span)))invalid();
        }
    }
    static void classification(Proposal detected,Proposal classified) {
        if(classified==null || classified.mentions()==null)invalid();
        var allowed=anchors(detected);var seen=anchors(classified);
        if(!allowed.equals(seen))invalid();
    }
    private static Set<Anchor> anchors(Proposal proposal) {
        var result=new HashSet<Anchor>();
        for(var m:proposal.mentions()) {
            if(m==null || m.name()==null || m.name().evidence()==null || m.name().evidence().isEmpty())invalid();
            for(var e:m.name().evidence())if(!result.add(new Anchor(m.kind(),m.name().value(),e)))invalid();
        }
        return result;
    }
    private static boolean unknown(Grounded<?> g) {return g!=null && g.value()!=null && g.value().name().equals("UNKNOWN") && g.evidence()!=null && g.evidence().isEmpty();}
    private static void invalid(){throw new IllegalArgumentException("Invalid experimental stage boundary");}
}
