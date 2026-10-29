package com.clinicalintelligence.extraction.application;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

/** Request-local rejection rules for explicit Spanish patterns. Never supplies a clinical label. */
public final class ContextEvidencePolicy {
    public enum Decision { KEEP, FOREIGN_EVIDENCE, EXPLICIT_CONFLICT }
    private record Span(int start,int end) {}
    private record Unit(int start,int end) {}
    private static final Pattern NON_RELATIVE=Pattern.compile("(?iuU),\\s*sin\\s+parentesco\\s+con\\s+(?:el|la)\\s+paciente\\s*,");
    private static final Pattern SPACE=Pattern.compile("\\s+",Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ADVERSATIVE=Pattern.compile("(?iuU)\\h+(?:pero|sin embargo)\\h+");
    private static final Pattern NEW_CLAUSE=Pattern.compile("(?iuU)\\h+y\\h+(?=(?:(?:el|la|al)\\h+(?:paciente|madre|padre|acompañante)|hoy|ayer|mañana)\\b)");
    private static final Pattern SUBJECT=Pattern.compile("(?iuU)\\b(?:(madre|padre|hermana|hermano|abuela|abuelo|familiar)(?:\\s+(?:del|de la)\\s+paciente)?|(paciente)|(acompañante|vecino|vecina|cuidador|cuidadora))\\b");
    private static final Map<String,Pattern> RULES=new ConcurrentHashMap<>();
    private final String note;
    private final List<Unit> paragraphs;
    private final boolean simultaneousHeader;
    private static final Pattern BLANK_PARAGRAPH=Pattern.compile("(?:(?>\\r\\n|[\\r\\n\\u0085\\u2028\\u2029])\\h*){2,}");
    private final int[] offsets;
    private final List<Unit> units;
    private final Map<Text,List<Span>> anchors=new HashMap<>();
    private final Map<Integer,List<Span>> heads=new HashMap<>();
    private final EvidencePolicy evidence=new EvidencePolicy();

    public ContextEvidencePolicy(String note,List<Mention> proposals) {
        this.note=note;offsets=new int[note.codePointCount(0,note.length())+1];
        int cp=0;for(int c=0;c<note.length();) {offsets[cp++]=c;c+=Character.charCount(note.codePointAt(c));}offsets[cp]=note.length();
        // Protect literal names so compound names and internal punctuation keep their coordinates.
        for(var m:proposals)if(evidence.validText(note,m.name()))anchors(m.name());
        units=split();
        var blocks=new ArrayList<Unit>();var blank=BLANK_PARAGRAPH.matcher(note);int from=0;
        while(blank.find()) {blocks.add(new Unit(from,blank.start()));from=blank.end();}
        blocks.add(new Unit(from,note.length()));paragraphs=List.copyOf(blocks);
        simultaneousHeader=matches(grammar(note),"\\bsimult[aá]neo\\s+sin\\s+resoluci[oó]n\\s*:");
        for(var spans:anchors.values())for(var s:spans)heads.computeIfAbsent(unit(s),ignored -> new ArrayList<>()).add(s);
        heads.replaceAll((k,v) -> v.stream().distinct().sorted(Comparator.comparingInt(Span::start)).toList());
    }
    public Decision check(Mention owner,Field field,Grounded<?> proposed) {
        if(proposed.value().name().equals("UNKNOWN"))return Decision.KEEP;
        var selected=anchors(owner.name());
        if(selected.isEmpty())return Decision.FOREIGN_EVIDENCE;
        for(var citation:proposed.evidence()) {
            var span=new Span(offsets[citation.start()],offsets[citation.end()]);
            boolean associated=false;
            for(var name:selected) {
                var u=units.get(unit(name));
                if(overlap(span,u) || field==Field.SUBJECT && sectionAntecedent(span,name,u,proposed)) {associated=true;break;}
            }
            if(!associated)return Decision.FOREIGN_EVIDENCE;
        }
        for(var name:selected) {
            int index=unit(name);var u=units.get(index);
            int start=u.start(),end=u.end();
            // A coordinated list can share a prefix, but never inherit across another predicate.
            var peers=heads.getOrDefault(index,List.of());
            int sharedStart=name.start();
            for(int i=peers.size()-1;i>=0;i--) {
                var previous=peers.get(i);
                if(previous.end()>sharedStart || previous.start()>=name.start())continue;
                String gap=grammar(note.substring(previous.end(),sharedStart));
                if(!gap.matches("(?:y|e|ni)"))break;
                sharedStart=previous.start();
            }
            for(var other:peers)if(other.start()>name.start()){end=Math.min(end,other.start());break;}
            String before=grammar(note.substring(start,sharedStart));
            String after=grammar(note.substring(name.end(),end));
            if(field==Field.SUBJECT && !SUBJECT.matcher(subjectGrammar(note.substring(start,end))).find()
                    && proposed.evidence().stream().anyMatch(e -> SUBJECT.matcher(subjectGrammar(e.quote())).find())) {
                boolean forward=false;
                for(var e:proposed.evidence()) {
                    var prefix=new Span(offsets[e.start()],Math.min(offsets[e.end()],u.start()));
                    if(prefix.start()<prefix.end() && sectionAntecedent(prefix,name,u,proposed)) {forward=true;break;}
                }
                if(!forward)return Decision.FOREIGN_EVIDENCE;
            }
            Set<String> allowed=allowed(owner.kind(),field,before,after,grammar(note.substring(start,end)));
            if(allowed!=null && !allowed.contains(proposed.value().name()))return Decision.EXPLICIT_CONFLICT;
        }
        return Decision.KEEP;
    }
    private Set<String> allowed(Kind kind,Field field,String before,String after,String local) {
        if(field==Field.ASSERTION) {
            if(tail(before,"no\\s+se\\s+sospecha"))return Set.of("AFFIRMED","NEGATED","CONDITIONAL");
            if(tail(before,"no\\s+se\\s+confirma"))return Set.of("SUSPECTED","NEGATED","CONDITIONAL");
            if(tail(before,"no\\s+(?:niega|deja\\s+de\\s+negar)"))return Set.of();
            if(tail(before,"(?:no\\s+(?:se\\s+)?puede\\s+descartar|no\\s+se\\s+descarta|no\\s+puede\\s+descartarse)"))return Set.of("SUSPECTED");
            if(tail(before,"(?:niega|sin(?:\\s+diagn[oó]stico\\s+de)?|se\\s+descarta|se\\s+descart[oó]|no\\s+(?:presenta|tiene)|no\\s+se\\s+(?:realiz[oó]|realiza|prescribe|solicita))") || head(after,"(?:descartad[ao]|negad[ao]|ausente)"))return Set.of("NEGATED");
            if(tail(before,"(?:se\\s+sospecha|se\\s+sospech[oó]|sospecha\\s+de|posible|probable)"))return Set.of("SUSPECTED");
            if(before.matches("(?iuU).*\\bsi\\b.+\\b(?:solicitar[aá]|realizar[aá]|planifica)\\s*"))return Set.of("CONDITIONAL");
            if(tail(before,"(?:se\\s+confirma|se\\s+confirm[oó]|confirma|tiene|diagn[oó]stico\\s+confirmado\\s+de)") || head(after,"confirmad[ao]"))return Set.of("AFFIRMED");
        }
        if(field==Field.SUBJECT) {
            var matcher=SUBJECT.matcher(subjectGrammar(local));String subject=null;
            while(matcher.find()) {
                String found=matcher.group(1)!=null?"FAMILY":matcher.group(2)!=null?"PATIENT":"OTHER";
                if(subject!=null && !subject.equals(found))return null; // unresolved syntax, never select a convenient subject
                subject=found;
            }
            if(subject!=null)return Set.of(subject);
        }
        if(field==Field.TEMPORALITY) {
            // Dates of orders are not dates of acts. Only an explicit 'para ...' overrides today.
            if(kind==Kind.PROCEDURE && matches(local,"\\bpara\\s+(?:mañana|otra\\s+fecha|la\\s+pr[oó]xima\\s+semana)\\b"))return Set.of("FUTURE");
            var dates=new HashSet<String>();
            if(matches(local,"\\b(?:ayer|antecedente|antecedentes|hist[oó]ric[ao]|previamente)\\b"))dates.add("HISTORICAL");
            if(matches(local,"\\b(?:hoy|actual|actualmente|durante\\s+esta\\s+visita|en\\s+esta\\s+visita)\\b"))dates.add("CURRENT");
            if(matches(local,"\\b(?:mañana|pr[oó]ximamente|futur[ao])\\b"))dates.add("FUTURE");
            if(dates.size()==1) {
                if(kind==Kind.PROCEDURE && dates.contains("CURRENT") && matches(local,"\\b(?:solicita|solicit[oó]|ordena|pide)\\b") && !matches(local,"\\b(?:realiza|realiz[oó])\\b"))return null;
                return dates;
            }
        }
        if(field==Field.PRESCRIPTION_STATUS) {
            if(tail(before,"no\\s+se\\s+(?:prescribe|indica)"))return Set.of("REPORTED","SUSPENDED");
            if(tail(before,"no\\s+se\\s+suspende"))return Set.of("PRESCRIBED","REPORTED");
            if(tail(before,"(?:se\\s+suspende|suspendi[oó]|suspende)"))return Set.of("SUSPENDED");
            if(tail(before,"(?:se\\s+prescribe|se\\s+indica|prescribe|prescripci[oó]n(?:\\s+actual)?(?:\\s+del\\s+paciente)?)"))return Set.of("PRESCRIBED");
            if(tail(before,"(?:refiere\\s+(?:tomar|usar|haber\\s+usado)|toma|usa)"))return Set.of("REPORTED");
        }
        if(field==Field.PERFORMANCE_STATUS) {
            if(tail(before,"no\\s+se\\s+(?:solicita|solicit[oó]|ordena|pide)"))return Set.of("PERFORMED","PLANNED","NOT_PERFORMED","CANCELLED");
            if(tail(before,"no\\s+se\\s+(?:cancel[oó]|cancela)"))return Set.of("PERFORMED","ORDERED","PLANNED","NOT_PERFORMED");
            if(head(after,"realizad[ao]"))return Set.of("PERFORMED");
            if(head(after,"cancelad[ao]"))return Set.of("CANCELLED");
            if(tail(before,"no\\s+se\\s+(?:realiz[oó]|realiza)"))return Set.of("NOT_PERFORMED");
            if(tail(before,"(?:se\\s+cancel[oó]|se\\s+cancela)"))return Set.of("CANCELLED");
            if(tail(before,"(?:se\\s+realiz[oó]|se\\s+realiza)"))return Set.of("PERFORMED");
            if(tail(before,"(?:se\\s+solicita|se\\s+solicit[oó]|se\\s+ordena|se\\s+pide)"))return Set.of("ORDERED");
            if(tail(before,"(?:se\\s+planifica|se\\s+realizar[aá]|se\\s+solicitar[aá])"))return Set.of("PLANNED");
        }
        return null;
    }
    /** Groups only directly supported opposite assertions within an explicit unresolved header. */
    public String simultaneousConflictKey(Mention mention) {
        if(!simultaneousHeader)return null;
        var context=mention.context();
        if(context.temporality().value()!=Temporality.CURRENT || context.assertion().value()!=Assertion.AFFIRMED && context.assertion().value()!=Assertion.NEGATED)return null;
        var subject=subjectIdentity(mention);
        if(subject==null)return null;
        Integer blockStart=null;
        for(var name:anchors(mention.name())) {
            var block=paragraphs.stream().filter(p -> p.start()<=name.start() && name.end()<=p.end()).findFirst().orElse(null);
            if(block==null || blockStart!=null && blockStart!=block.start())return null;
            String prefix=grammar(note.substring(block.start(),name.start()));
            var marker=Pattern.compile("(?iuU)\\bsimult[aá]neo\\s+sin\\s+resoluci[oó]n\\s*:").matcher(prefix);
            if(!marker.find() || matches(prefix.substring(marker.end()),"[.!?]"))return null;
            blockStart=block.start();
        }
        return mention.kind()+":"+mention.name().value()+":"+subject+":"+blockStart;
    }
    private String subjectIdentity(Mention mention) {
        String identity=null;
        for(var span:anchors(mention.name())) {
            var u=units.get(unit(span));String local=subjectGrammar(note.substring(u.start(),u.end()));
            var subjects=SUBJECT.matcher(subjectGrammar(local));if(!subjects.find())return null;
            String base=subjects.group(1)!=null?"FAMILY:"+subjects.group(1):subjects.group(2)!=null?"PATIENT":"OTHER:"+subjects.group(3);
            var qualifier=Pattern.compile("(?iuU)^\\s*(?:([\\p{L}\\p{N}_-]+)\\s+)?(?:tiene|niega|presenta|refiere|consulta)\\b").matcher(local.substring(subjects.end()));
            if(!qualifier.find() || subjects.find())return null;
            String next=(base+":"+(qualifier.group(1)==null?"":qualifier.group(1))).toLowerCase(Locale.ROOT);
            if(identity!=null && !identity.equals(next))return null;
            identity=next;
        }
        return identity;
    }
    private boolean sectionAntecedent(Span citation,Span name,Unit own,Grounded<?> proposed) {
        if(citation.end()>own.start())return false; // never infer a retrospective relationship
        if(proposed.evidence().stream().noneMatch(e -> overlap(new Span(offsets[e.start()],offsets[e.end()]),own)))return false;
        String quote=grammar(note.substring(citation.start(),citation.end()));
        var source=SUBJECT.matcher(subjectGrammar(quote));if(!source.find())return false;
        String subject=source.group(1)!=null?"FAMILY":source.group(2)!=null?"PATIENT":"OTHER";
        if(!subject.equals(proposed.value().name()))return false;
        String continuity=note.substring(citation.start(),own.start());
        if(BLANK_PARAGRAPH.matcher(continuity).find())return false;
        var changes=SUBJECT.matcher(subjectGrammar(continuity));
        while(changes.find()) {
            String next=changes.group(1)!=null?"FAMILY":changes.group(2)!=null?"PATIENT":"OTHER";
            if(!next.equals(subject))return false;
        }
        return true;
    }
    private List<Span> anchors(Text name) {
        return anchors.computeIfAbsent(name,key -> {
            var result=new LinkedHashSet<Span>();
            for(var e:key.evidence()) {
                int from=0,at;
                while((at=e.quote().indexOf(key.value(),from))>=0) {
                    int start=offsets[e.start()]+at;result.add(new Span(start,start+key.value().length()));from=at+1;
                }
            }
            return List.copyOf(result);
        });
    }
    private int unit(Span span) {
        int low=0,high=units.size()-1;
        while(low<high) {int mid=(low+high)>>>1;if(units.get(mid).end()<=span.start())low=mid+1;else high=mid;}
        return low;
    }
    private List<Unit> split() {
        var protectedSpans=new TreeMap<Integer,Integer>();
        var protectedRanges=new ArrayList<Span>();anchors.values().forEach(protectedRanges::addAll);
        var apposition=NON_RELATIVE.matcher(note);while(apposition.find())protectedRanges.add(new Span(apposition.start(),apposition.end()));
        var sorted=protectedRanges.stream().sorted(Comparator.comparingInt(Span::start)).toList();
        for(var span:sorted) {
            var last=protectedSpans.lastEntry();
            if(last!=null && span.start()<last.getValue())protectedSpans.put(last.getKey(),Math.max(last.getValue(),span.end()));
            else protectedSpans.put(span.start(),span.end());
        }
        var breaks=new TreeSet<Integer>();breaks.add(0);breaks.add(note.length());
        for(int i=0;i<note.length();i++) {
            char c=note.charAt(i);
            boolean boundary=c==';' || c=='\r' || c=='\n' || c=='\u0085' || c=='\u2028' || c=='\u2029'
                || c==',' && !(i>0 && i+1<note.length() && Character.isDigit(note.charAt(i-1)) && Character.isDigit(note.charAt(i+1)))
                || ".!?".indexOf(c)>=0 && (i+1==note.length() || EvidencePolicy.whitespace(note.codePointAt(i+1)));
            if(boundary && !inside(protectedSpans,i))breaks.add(i+1);
        }
        var coordinator=NEW_CLAUSE.matcher(note);while(coordinator.find())if(!inside(protectedSpans,coordinator.start()))breaks.add(coordinator.end());
        var matcher=ADVERSATIVE.matcher(note);while(matcher.find())if(!inside(protectedSpans,matcher.start()))breaks.add(matcher.start());
        var result=new ArrayList<Unit>();Integer last=null;
        for(int b:breaks) {if(last!=null && b>last)result.add(new Unit(last,b));last=b;}
        return List.copyOf(result);
    }
    private static boolean inside(NavigableMap<Integer,Integer> spans,int at) {var previous=spans.floorEntry(at);return previous!=null && at<previous.getValue();}
    private static boolean overlap(Span s,Unit u) {return s.start()<u.end() && u.start()<s.end();}
    private static boolean tail(String value,String rule) {return matches(value,"\\b(?:"+rule+")(?:\\s+(?:hoy|ayer|mañana))?(?:\\s+(?:el|la|un|una|diagn[oó]stico\\s+de))?\\s*[:,-]?\\s*$");}
    private static boolean head(String value,String rule) {return matches(value,"^\\s*[:,-]?\\s*(?:"+rule+")\\b");}
    private static boolean matches(String value,String regex) {return RULES.computeIfAbsent(regex,r -> Pattern.compile(r,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.UNICODE_CHARACTER_CLASS)).matcher(value).find();}
    private static String subjectGrammar(String value) {return NON_RELATIVE.matcher(grammar(value)).replaceAll(" ");}
    private static String grammar(String value) {return SPACE.matcher(Normalizer.normalize(value,Normalizer.Form.NFC)).replaceAll(" ").strip();}
}
