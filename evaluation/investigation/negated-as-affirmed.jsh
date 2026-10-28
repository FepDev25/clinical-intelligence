import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.*;
import java.util.*;
String note = "El paciente niega asma.";
Evidence whole = new Evidence(0,note.length(),note);
int i = note.indexOf("asma");
Text name = new Text("asma",List.of(new Evidence(i,i+4,"asma")));
Context context = new Context(new Grounded<>(Assertion.AFFIRMED,List.of(whole)),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.PATIENT,List.of(whole)));
Diagnosis diagnosis = new Diagnosis(name,null,context);
ProviderResponse response = new ProviderResponse(Completion.COMPLETE,new Proposal(List.of(diagnosis),List.of()),new ProviderMetadata("synthetic",ExecutionMode.SIMULATED,"fake","none"));
Result result = new ExtractionService(ignored -> response).extract(Command.note(note));
System.out.println("NEGATED_AS_ASSERTION="+result.diagnoses().getFirst().mention().context().assertion().value()+"; WARNINGS="+result.warnings().size());
/exit
