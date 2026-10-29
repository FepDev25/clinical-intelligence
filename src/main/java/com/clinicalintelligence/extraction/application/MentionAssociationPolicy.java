package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.util.*;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Request-local structural guard. Passing it is not semantic or clinical validation. */
public final class MentionAssociationPolicy {
    private record Span(int start,int end) {}
    private record Unit(int start,int end,int group) {}
    private record Head(Kind kind,String name,Span span) {}
    private static final Pattern COORD = Pattern.compile("(?iuU)\\h+(?:y|e|pero)\\h+");
    private static final Pattern LABEL = Pattern.compile("(?iu)^(?:dosis|frecuencia|(?:por\\s+)?v[ií]a|duraci[oó]n|posolog[ií]a)\\b");
    private static final Pattern ANAPHORA = Pattern.compile("(?iu)^(?:su\\s+(?:dosis|frecuencia|v[ií]a|duraci[oó]n|c[oó]digo)|para\\s+este\\s+medicamento)\\b");
    private static final Pattern SHARED = Pattern.compile("(?iuU)^\\s*(?:(?:para\\s+)?ambos|cada\\s+uno|para\\s+los\\s+dos)\\b");
    private static final Pattern ABBREVIATION = Pattern.compile("(?u)(?:\\p{L}\\.){2,}$");
    private static final Pattern SPACES = Pattern.compile("\\s+",Pattern.UNICODE_CHARACTER_CLASS);
    private final String note;
    private final int[] charOffsets;
    private final List<Unit> units;
    private final List<Head> heads;
    private final Map<Integer,List<Head>> headsByUnit = new HashMap<>();
    private final Map<Integer,List<Head>> headsByGroup = new HashMap<>();
    private final Map<Text,Set<Span>> anchorCache = new HashMap<>();
    private final Map<Text,List<Span>> orderedAnchors = new HashMap<>();
    private final Map<Kind,Map<Integer,List<Head>>> typedUnitHeads = new EnumMap<>(Kind.class);
    private final Map<Kind,Map<Integer,List<Head>>> typedGroupHeads = new EnumMap<>(Kind.class);
    private final Map<Integer,List<Head>> headsByStart = new HashMap<>();
    private final EvidencePolicy evidence = new EvidencePolicy();

    public MentionAssociationPolicy(String note,List<Mention> proposed) {
        this.note=note;
        charOffsets=new int[note.codePointCount(0,note.length())+1];
        int cp=0;
        for(int c=0;c<note.length();) {charOffsets[cp++]=c;c+=Character.charCount(note.codePointAt(c));}
        charOffsets[cp]=note.length();
        units=split();
        var found=new LinkedHashSet<Head>();
        for(var mention:proposed) if(evidence.validText(note,mention.name()))
            for(var span:anchors(mention.name()))found.add(new Head(mention.kind(),mention.name().value(),span));
        heads=List.copyOf(found);
        for(Head head:heads) {
            int index=unitOf(head.span());
            if(index<0)continue;
            headsByUnit.computeIfAbsent(index,ignored -> new ArrayList<>()).add(head);
            headsByGroup.computeIfAbsent(units.get(index).group(),ignored -> new ArrayList<>()).add(head);
        }
        for(Head head:heads) {
            int index=unitOf(head.span());if(index<0)continue;
            typedUnitHeads.computeIfAbsent(head.kind(),ignored -> new HashMap<>()).computeIfAbsent(index,ignored -> new ArrayList<>()).add(head);
            typedGroupHeads.computeIfAbsent(head.kind(),ignored -> new HashMap<>()).computeIfAbsent(units.get(index).group(),ignored -> new ArrayList<>()).add(head);
        }
        headsByUnit.forEach((index,list) -> {
            var byStart=new ArrayList<>(list);byStart.sort(Comparator.comparingInt(h -> h.span().start()));headsByStart.put(index,List.copyOf(byStart));
        });
        headsByUnit.values().forEach(MentionAssociationPolicy::sortByEnd);
        headsByGroup.values().forEach(MentionAssociationPolicy::sortByEnd);
        typedUnitHeads.values().forEach(map -> map.values().forEach(MentionAssociationPolicy::sortByEnd));
        typedGroupHeads.values().forEach(map -> map.values().forEach(MentionAssociationPolicy::sortByEnd));
    }
    public boolean supports(Mention owner,Text attribute,boolean code) {
        if(!evidence.validText(note,owner.name()) || !evidence.validText(note,attribute))return false;
        Set<Span> selected=anchors(owner.name());
        Set<Span> values=anchors(attribute);
        var linkedUnits=new HashMap<Integer,Boolean>();
        for(Span value:values) {
            int index=unitOf(value);
            if(index<0 || value.start()<units.get(index).start() || value.end()>units.get(index).end())return false;
            if(localOwner(owner,selected,index,value.start(),code) || code && prefixOwner(owner,selected,index,value))continue;
            if(!code && sharedOwner(owner,selected,index,value))continue;
            boolean linked=false;
            int current=index;
            var visited=new ArrayList<Integer>();
            while(current>0) {
                if(linkedUnits.containsKey(current)) {linked=linkedUnits.get(current);break;}
                if(!continuation(owner,current,attribute,code) || hasHead(owner,code,current))break;
                visited.add(current);
                Unit here=units.get(current),previous=units.get(current-1);
                if(previous.group()!=here.group()) {
                    if(!ANAPHORA.matcher(body(here)).find() || note.substring(previous.end(),here.start()).matches("(?sU).*\\R\\s*\\R.*"))break;
                    long owners=relevantGroup(previous.group(),owner,code).size();
                    if(owners!=1)break;
                }
                current--;
                if(localOwner(owner,selected,current,units.get(current).end(),code)) {linked=true;break;}
                if(hasHead(owner,code,current))break;
            }
            for(int unit:visited)linkedUnits.put(unit,linked);
            if(!linked)return false;
        }
        return !values.isEmpty();
    }
    private static void sortByEnd(List<Head> list) {list.sort(Comparator.comparingInt(h -> h.span().end()));}
    private List<Head> relevantUnit(int index,Mention owner,boolean code) {
        return code ? unitHeads(index) : typedUnitHeads.getOrDefault(owner.kind(),Map.of()).getOrDefault(index,List.of());
    }
    private List<Head> relevantGroup(int group,Mention owner,boolean code) {
        return code ? groupHeads(group) : typedGroupHeads.getOrDefault(owner.kind(),Map.of()).getOrDefault(group,List.of());
    }
    private boolean hasHead(Mention owner,boolean code,int unit) {return !relevantUnit(unit,owner,code).isEmpty();}
    private boolean localOwner(Mention owner,Set<Span> selected,int unit,int before,boolean code) {
        var eligible=relevantUnit(unit,owner,code);
        int low=0,high=eligible.size();
        while(low<high) {int mid=(low+high)>>>1;if(eligible.get(mid).span().end()<=before)low=mid+1;else high=mid;}
        if(low==0)return false;
        int end=eligible.get(low-1).span().end();
        for(int i=low-1;i>=0 && eligible.get(i).span().end()==end;i--) {
            var head=eligible.get(i);
            if(head.kind()!=owner.kind() || !selected.contains(head.span()))return false;
        }
        return true;
    }
    private boolean prefixOwner(Mention owner,Set<Span> selected,int index,Span value) {
        Unit unit=units.get(index);
        var ended=unitHeads(index);
        if(!ended.isEmpty() && ended.getFirst().span().end()<=value.start())return false;
        var following=headsByStart.getOrDefault(index,List.of());
        int low=0,high=following.size();
        while(low<high) {int mid=(low+high)>>>1;if(following.get(mid).span().start()<value.end())low=mid+1;else high=mid;}
        if(low==following.size())return false;
        int first=following.get(low).span().start();
        for(int i=low;i<following.size() && following.get(i).span().start()==first;i++) {
            var head=following.get(i);
            if(head.kind()!=owner.kind() || !selected.contains(head.span()))return false;
        }
        String prefix=grammar(note.substring(unit.start(),value.start()));
        String link=grammar(note.substring(value.end(),first));
        return (prefix.isEmpty() || prefix.matches("(?iu)c[oó]digo(?: externo| textual)?[ :]*"))
                && link.matches("(?iu)[ :—–-]*(?:(?:de|para|diagn[oó]stico|procedimiento)[ :—–-]*)?");
    }
    private List<Head> unitHeads(int index) {return headsByUnit.getOrDefault(index,List.of());}
    private List<Head> groupHeads(int group) {return headsByGroup.getOrDefault(group,List.of());}
    private boolean continuation(Mention owner,int index,Text attribute,boolean code) {
        String text=body(units.get(index));
        if(ANAPHORA.matcher(text).find())return true;
        if(code) {
            String label="(?iu)c[oó]digo(?: externo| textual)?(?:\\s+(?:de|para)\\s+"+Pattern.quote(grammar(owner.name().value()))+")?\\s*[:=]?\\s*";
            if(text.matches(label+Pattern.quote(grammar(attribute.value()))))return true;
            if(text.matches(label))return true;
            return text.equals(grammar(attribute.value())) && index>0 && body(units.get(index-1)).matches(label);
        }
        // A value proposed by the model is never permission to cross a line.
        // Recognize only structural prescription fragments, independently of fields.
        var named=Pattern.compile("(?iu)^(?:dosis|frecuencia|v[ií]a|duraci[oó]n)\\s+(?:de|para)\\s+(.+?)\\s*:").matcher(text);
        if(named.find() && !named.group(1).equals(grammar(owner.name().value())))return false;
        return LABEL.matcher(text).find()
                || text.matches("(?iu)^(?:[0-9][0-9.,]*|un[ao]?|media?|medio|dos|tres)(?:\\s|\\p{L}).*")
                || text.matches("(?iu)^(?:cada|seg[uú]n|si|durante)\\b.*")
                || text.matches("(?iu)(?:oral|intravenosa|intramuscular|subcutánea|tópica|inhalada|v\\.o\\.|i\\.v\\.|i\\.m\\.)");
    }
    private boolean sharedOwner(Mention owner,Set<Span> selected,int index,Span value) {
        Unit unit=units.get(index);var marker=SHARED.matcher(note.substring(unit.start(),unit.end()));
        if(!marker.find())return false;
        if(hasHead(owner,false,index))return false;
        var candidates=relevantGroup(unit.group(),owner,false);
        int low=0,high=candidates.size();
        while(low<high) {int mid=(low+high)>>>1;if(candidates.get(mid).span().end()<=unit.start())low=mid+1;else high=mid;}
        if(low<2)return false;
        Head a=candidates.get(low-2),b=candidates.get(low-1);
        if(a.name().equals(b.name()) || a.span().end()>b.span().start())return false;
        if(low>2) {
            Head earlier=candidates.get(low-3);
            if(earlier.span().end()>a.span().start() || grammar(note.substring(earlier.span().end(),a.span().start())).matches("(?iu)(?:y|e|,)"))return false;
        }
        return (selected.contains(a.span()) || selected.contains(b.span()))
                && grammar(note.substring(a.span().end(),b.span().start())).matches("(?iu)(?:y|e|,)")
                && note.substring(b.span().end(),unit.start()).matches("(?U)[\\s,;]*")
                && value.start()>=unit.start()+marker.end();
    }
    private Set<Span> anchors(Text text) {
        Set<Span> cached=anchorCache.get(text);
        if(cached!=null)return cached;
        var result=new LinkedHashSet<Span>();
        for(Evidence span:text.evidence()) {
            int from=0,at;
            while((at=span.quote().indexOf(text.value(),from))>=0) {
                int start=charOffsets[span.start()]+at;
                result.add(new Span(start,start+text.value().length()));from=at+1;
            }
        }
        var sorted=result.stream().sorted(Comparator.comparingInt(Span::start)).toList();
        orderedAnchors.put(text,sorted);
        Set<Span> frozen=Collections.unmodifiableSet(result);
        anchorCache.put(text,frozen);
        return frozen;
    }
    public boolean scopeContainsName(Text name,Evidence scope) {
        int start=charOffsets[scope.start()],end=charOffsets[scope.end()];
        anchors(name);
        var sorted=orderedAnchors.get(name);int low=0,high=sorted.size();
        while(low<high) {int mid=(low+high)>>>1;if(sorted.get(mid).start()<start)low=mid+1;else high=mid;}
        return low<sorted.size() && sorted.get(low).end()<=end;
    }
    private int unitOf(Span span) {
        int first=unitAt(span.start()),last=unitAt(span.end()-1);
        return first<0 || last<0 || units.get(first).group()!=units.get(last).group() ? -1 : last;
    }
    private int unitAt(int position) {
        int low=0,high=units.size()-1;
        while(low<=high) {int mid=(low+high)>>>1;Unit u=units.get(mid);if(position<u.start())high=mid-1;else if(position>=u.end())low=mid+1;else return mid;}
        return -1;
    }
    private static String grammar(String text) {return SPACES.matcher(Normalizer.normalize(text,Normalizer.Form.NFC)).replaceAll(" ").strip();}
    private String body(Unit unit) {return grammar(note.substring(unit.start(),unit.end()));}
    private List<Unit> split() {
        var result=new ArrayList<Unit>();int start=0,group=0;
        for(int i=0;i<note.length();) {
            char c=note.charAt(i);int length=0;boolean hard=false;
            if(c=='\r'||c=='\n'||c==0x85||c==0x2028||c==0x2029) {length=c=='\r'&&i+1<note.length()&&note.charAt(i+1)=='\n'?2:1;hard=EvidencePolicy.blank(note.substring(start,i));}
            else if(c==';'||c==',') {if(c!=',' || i==0 || i+1==note.length() || !Character.isDigit(note.charAt(i-1)) || !Character.isDigit(note.charAt(i+1)))length=1;}
            else if((c=='.'||c=='!'||c=='?') && (i+1==note.length()||EvidencePolicy.whitespace(note.charAt(i+1)))) {
                if(c!='.' || !ABBREVIATION.matcher(note.substring(Math.max(start,i-10),i+1)).find()) {length=1;hard=true;}
            } else if(EvidencePolicy.whitespace(c)) {var m=COORD.matcher(note);m.region(i,note.length());if(m.lookingAt())length=m.end()-i;}
            if(length==0) {i++;continue;}
            if(!EvidencePolicy.blank(note.substring(start,i)))result.add(new Unit(start,i,group));
            if(hard)group++;
            i+=length;start=i;
        }
        if(start<note.length()&&!EvidencePolicy.blank(note.substring(start)))result.add(new Unit(start,note.length(),group));
        return List.copyOf(result);
    }
}
