package com.clinicalintelligence.extraction.infrastructure;

import static com.clinicalintelligence.extraction.domain.ClinicalModel.*;
import com.clinicalintelligence.extraction.application.ExtractionProvider;
import java.util.Objects;
import java.util.function.Function;

/** Explicit fake: injected script, no inference, file lookup, credentials or network. */
public final class ScriptedExtractionProvider implements ExtractionProvider {
    private final Function<ProviderRequest, Proposal> script;
    public ScriptedExtractionProvider(Function<ProviderRequest, Proposal> script) {
        this.script = Objects.requireNonNull(script);
    }
    @Override public ProviderResponse extract(ProviderRequest request) {
        return new ProviderResponse(Completion.COMPLETE, script.apply(request),
                new ProviderMetadata("script-v1", ExecutionMode.SIMULATED, "deterministic-script", "none"));
    }
}
