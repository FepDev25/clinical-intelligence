package com.clinicalintelligence.extraction.infrastructure.gemini;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
class StudyBudgetTest {
    @Test void reserveWorstCaseBeforeCallThenReconcileActualUsage() {
        var b=new StudyBudget(new BigDecimal("20"));
        var hold=b.reserve("gemini-3.8-flash",LocalDate.of(2026,10,28));
        assertEquals(new BigDecimal("1.032192"),b.committed());
        b.settle(hold,new GeminiProtocol.Usage(100L,20L,10L,0L,130L));
        assertEquals(0,b.committed().compareTo(new BigDecimal("0.0001875")));
        assertThrows(IllegalStateException.class,() -> b.settle(hold,new GeminiProtocol.Usage(100L,20L,10L,0L,130L)));
    }
    @Test void failedOrMissingUsageRetainsReservationAndStopsFurtherSpending() {
        var b=new StudyBudget(new BigDecimal("20"));var hold=b.reserve("gemini-4-argon",LocalDate.of(2026,10,28));
        b.uncertain(hold);assertEquals(new BigDecimal("2.752512"),b.committed());
        assertThrows(IllegalStateException.class,() -> b.reserve("gemini-3.8-flash",LocalDate.of(2026,10,28)));
    }
    @Test void neverStartsCallWithoutFullWorstCaseHeadroomOrWithExpiredTariff() {
        var b=new StudyBudget(new BigDecimal("1"));
        assertThrows(IllegalStateException.class,() -> b.reserve("gemini-3.8-flash",LocalDate.of(2026,10,28)));
        assertEquals(BigDecimal.ZERO,b.committed());
        assertThrows(IllegalArgumentException.class,() -> b.reserve("gemini-4-argon",LocalDate.of(2026,12,1)));
        assertThrows(IllegalArgumentException.class,() -> b.reserve("unverified",LocalDate.of(2026,10,28)));
    }
    @Test void derivesMissingThinkingOnlyFromReconciledExplicitTotal() {
        var b=new StudyBudget(new BigDecimal("20"));var h=b.reserve("gemini-3.8-flash",LocalDate.of(2026,10,28));
        b.settle(h,new GeminiProtocol.Usage(100L,20L,null,null,130L));
        assertEquals(0,b.committed().compareTo(new BigDecimal("0.0001875")));
    }
    @Test void inconsistentOrOverflowingCountersCannotReleaseBudget() {
        for(var u:java.util.List.of(new GeminiProtocol.Usage(1L,1L,1L,0L,1L),new GeminiProtocol.Usage(Long.MAX_VALUE,1L,1L,0L,null),new GeminiProtocol.Usage(null,null,null,null))) {
            var b=new StudyBudget(new BigDecimal("20"));var h=b.reserve("gemini-3.8-flash",LocalDate.of(2026,10,28));b.settle(h,u);
            assertTrue(b.stopped());assertEquals(new BigDecimal("1.032192"),b.committed());
        }
    }
}
