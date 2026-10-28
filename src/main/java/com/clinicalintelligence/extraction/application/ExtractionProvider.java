package com.clinicalintelligence.extraction.application;

import com.clinicalintelligence.extraction.domain.ClinicalModel.ProviderRequest;
import com.clinicalintelligence.extraction.domain.ClinicalModel.ProviderResponse;

/** The core never receives SDK objects, HTTP requests or persisted EHR entities. */
@FunctionalInterface
public interface ExtractionProvider {
    ProviderResponse extract(ProviderRequest request);

    enum Failure { TIMEOUT, UNAVAILABLE, CAPACITY_EXCEEDED, OVERLOADED, INVALID_RESPONSE, TRUNCATED_RESPONSE }
    final class ProviderFailure extends RuntimeException {
        private final Failure failure;
        public ProviderFailure(Failure failure) { super(failure.name()); this.failure = failure; }
        public Failure failure() { return failure; }
    }
}
