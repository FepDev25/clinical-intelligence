import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.*;
import java.util.*;
String note = "Se prescribe Alfa 5 mg. Se prescribe Beta 9 mg.";
Evidence whole = new Evidence(0, note.length(), note);
int ni = note.indexOf("Alfa");
int di = note.indexOf("9 mg");
Text name = new Text("Alfa", List.of(new Evidence(ni, ni+4, "Alfa")));
Text dose = new Text("9 mg", List.of(new Evidence(di, di+4, "9 mg")));
Context context = new Context(new Grounded<>(Assertion.AFFIRMED,List.of(whole)),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
Medication med = new Medication(name,null,context,new Grounded<>(PrescriptionStatus.PRESCRIBED,List.of(whole)),dose,null,null,null);
ProviderResponse response = new ProviderResponse(Completion.COMPLETE,new Proposal(List.of(med),List.of()),new ProviderMetadata("synthetic",ExecutionMode.SIMULATED,"fake","none"));
Result result = new ExtractionService(ignored -> response).extract(Command.note(note));
System.out.println("WRONG_OTHER_CLAUSE_DOSE="+((Medication)result.medications().getFirst().mention()).dosage().value()+"; WARNINGS="+result.warnings().size());
/exit
