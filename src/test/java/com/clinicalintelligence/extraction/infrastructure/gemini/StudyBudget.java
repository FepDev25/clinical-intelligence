package com.clinicalintelligence.extraction.infrastructure.gemini;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
/** Sequential study ledger. Failed/unknown billing never releases a reservation. */
final class StudyBudget {
    record Rate(String model,BigDecimal input,BigDecimal output,LocalDate expires) {}
    record Hold(UUID id,Rate rate,BigDecimal usd) {}
    private final BigDecimal limit;
    private BigDecimal committed=BigDecimal.ZERO;
    private final Map<UUID,Hold> pending=new HashMap<>();
    private boolean stopped;
    StudyBudget(BigDecimal limit) {
        if(limit==null || limit.signum()<=0 || limit.scale()>8)throw new IllegalArgumentException("Invalid study budget");
        this.limit=limit;
    }
    static Rate rate(String model,LocalDate date) {
        Rate rate=switch(model) {
            case "gemini-3.8-flash" -> new Rate(model,new BigDecimal("0.75"),new BigDecimal("3.75"),LocalDate.of(2026,12,31));
            case "gemini-4-argon" -> new Rate(model,new BigDecimal("2.00"),new BigDecimal("10.00"),LocalDate.of(2026,11,30));
            default -> throw new IllegalArgumentException("Unsupported study tariff");
        };
        if(date==null || date.isBefore(LocalDate.of(2026,10,28)) || date.isAfter(rate.expires()))throw new IllegalArgumentException("Study tariff requires reverification");
        return rate;
    }
    synchronized void headroom(String model,LocalDate date) {
        if(stopped || !pending.isEmpty() || committed.add(price(rate(model,date),1_048_576,65_536)).compareTo(limit)>0)throw new IllegalStateException("BUDGET_HEADROOM_EXHAUSTED");
    }
    synchronized Hold reserve(String model,LocalDate date) {
        if(stopped || !pending.isEmpty())throw new IllegalStateException("Study stopped or concurrent reservation");
        var r=rate(model,date);var max=price(r,1_048_576,65_536);
        if(committed.add(max).compareTo(limit)>0)throw new IllegalStateException("BUDGET_HEADROOM_EXHAUSTED");
        var hold=new Hold(UUID.randomUUID(),r,max);pending.put(hold.id(),hold);committed=committed.add(max);return hold;
    }
    synchronized void settle(Hold hold,GeminiProtocol.Usage usage) {
        owned(hold);
        try {
            if(usage==null || usage.inputTokens()==null || usage.outputTokens()==null)throw new IllegalArgumentException();
            long input=usage.inputTokens(),output=usage.outputTokens();Long thought=usage.thoughtTokens();
            if(thought==null && usage.totalTokens()!=null)thought=Math.subtractExact(Math.subtractExact(usage.totalTokens(),input),output);
            if(thought==null)throw new IllegalArgumentException();
            long generated=Math.addExact(output,thought),total=Math.addExact(input,generated);
            if(input<0 || output<0 || thought<0 || input>1_048_576 || generated>65_536 || usage.totalTokens()!=null && usage.totalTokens()!=total)throw new IllegalArgumentException();
            var actual=price(hold.rate(),input,generated);if(actual.compareTo(hold.usd())>0)throw new IllegalArgumentException();
            committed=committed.subtract(hold.usd()).add(actual);pending.remove(hold.id());
        } catch(RuntimeException invalid) {uncertain(hold);}
    }
    synchronized void uncertain(Hold hold){owned(hold);pending.remove(hold.id());stopped=true;}
    private void owned(Hold hold){if(hold==null || !hold.equals(pending.get(hold.id())))throw new IllegalStateException("Unknown study reservation");}
    static BigDecimal price(Rate rate,long input,long output){return rate.input().multiply(BigDecimal.valueOf(input)).add(rate.output().multiply(BigDecimal.valueOf(output))).movePointLeft(6).stripTrailingZeros();}
    synchronized BigDecimal committed(){return committed;}
    synchronized boolean stopped(){return stopped;}
}
