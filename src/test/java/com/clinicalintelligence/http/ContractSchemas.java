package com.clinicalintelligence.http;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ContractSchemas {
    private static final Map<String,JsonSchema> SCHEMAS=load();
    static void validate(JsonNode body,int status) {
        var errors=SCHEMAS.get(status==200?"ExtractionResponse":"Problem").validate(body);
        assertTrue(errors.isEmpty(),() -> errors.toString());
    }
    private static Map<String,JsonSchema> load() {
        try {
            var mapper=new ObjectMapper();
            var source=mapper.readTree(Files.readString(Path.of("openapi/openapi.json"))).at("/components/schemas");
            JsonNode defs=mapper.readTree(source.toString().replace("#/components/schemas/","#/$defs/"));
            var factory=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            var config=SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
            var result=new HashMap<String,JsonSchema>();
            for(String name:List.of("ExtractionResponse","Problem")) {
                ObjectNode document=mapper.createObjectNode().put("$ref","#/$defs/"+name);
                document.set("$defs",defs);
                result.put(name,factory.getSchema(document,config));
            }
            return Map.copyOf(result);
        } catch(Exception invalid) { throw new IllegalStateException("Cannot load approved OpenAPI schemas",invalid); }
    }
}

