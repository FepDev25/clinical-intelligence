import com.clinicalintelligence.extraction.application.*;
import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.util.*;
for(int count:new int[]{500,1000,2000,4000}) {
 String note="Alfa 5 mg\n".repeat(count);
 var whole=new Evidence(0,note.codePointCount(0,note.length()),note);
 var name=new Text("Alfa",List.of(whole));
 var dose=new Text("5 mg",List.of(whole));
 var context=new Context(new Grounded<>(Assertion.AFFIRMED,List.of(whole)),new Grounded<>(Temporality.UNKNOWN,List.of()),new Grounded<>(Subject.UNKNOWN,List.of()));
 var m=new Medication(name,null,context,new Grounded<>(PrescriptionStatus.PRESCRIBED,List.of(whole)),dose,null,null,null);
 long start=System.nanoTime();
 var policy=new MentionAssociationPolicy(note,List.of(m));
 long built=System.nanoTime();
 boolean supported=policy.supports(m,dose,false);
 long end=System.nanoTime();
 System.out.printf("%d,%d,%s,%d,%d%n",count,note.length(),supported,built-start,end-built);
}
/exit
