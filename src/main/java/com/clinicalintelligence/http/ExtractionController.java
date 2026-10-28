package com.clinicalintelligence.http;

import com.clinicalintelligence.extraction.application.ExtractionService;
import com.clinicalintelligence.extraction.application.ExtractionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;

@RestController
final class ExtractionController {
    private final WireContract wire;
    private final ExtractionService service;
    private final ObjectMapper json = new ObjectMapper();
    ExtractionController(WireContract wire,ExtractionService service) { this.wire=wire; this.service=service; }

    @PostMapping("/api/v1/extractions")
    void extract(HttpServletRequest request,HttpServletResponse response) throws IOException {
        UUID id=(UUID)request.getAttribute(ApiFilter.ID);
        try {
            if(id==null || request.getAttribute(ApiFilter.IDENTITY)==null) throw new HttpFailure("INVALID_API_KEY");
            var result=service.extract(wire.decode((byte[])request.getAttribute(ApiFilter.BODY)));
            byte[] bytes=json.writeValueAsBytes(wire.encode(result,id));
            response.setStatus(200); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
            response.getOutputStream().write(bytes);
        } catch(HttpFailure failure) { Problems.write(response,id==null?UUID.randomUUID():id,failure.code(),failure.pointer()); }
        catch(ExtractionException failure) { Problems.write(response,id,failure.code().name(),null); }
    }
}

