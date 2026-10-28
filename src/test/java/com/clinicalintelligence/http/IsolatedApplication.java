package com.clinicalintelligence.http;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.*;
import java.util.Map;

/** Remove inherited configuration before Spring ConfigData can import any user file. */
final class IsolatedApplication {
    private IsolatedApplication() {}
    static SpringApplicationBuilder builder(Class<?>... sources) {
        var environment=new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("isolated-test-config",Map.of(
                "spring.config.location","classpath:/application.properties",
                "spring.config.additional-location","",
                "spring.config.import","")));
        return new SpringApplicationBuilder(sources).environment(environment);
    }
}
