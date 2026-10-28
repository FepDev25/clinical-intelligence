package com.clinicalintelligence.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

final class Problems {
    private static final ObjectMapper JSON = new ObjectMapper();
    private Problems() {}
    static void write(HttpServletResponse response, UUID id, String code, String pointer) throws IOException {
        int status = switch(code) {
            case "INVALID_API_KEY" -> 401;
            case "INSUFFICIENT_SCOPE" -> 403;
            case "METHOD_NOT_ALLOWED" -> 405;
            case "NOT_ACCEPTABLE" -> 406;
            case "INPUT_LIMIT_EXCEEDED" -> 413;
            case "UNSUPPORTED_MEDIA_TYPE","UNSUPPORTED_CONTENT_ENCODING" -> 415;
            case "UNSUPPORTED_LOCALE","PROVIDER_CAPACITY_EXCEEDED" -> 422;
            case "CONSUMER_RATE_LIMITED" -> 429;
            case "PROVIDER_INVALID_RESPONSE","PROVIDER_TRUNCATED_RESPONSE" -> 502;
            case "PROVIDER_UNAVAILABLE","SERVICE_OVERLOADED" -> 503;
            case "PROVIDER_TIMEOUT" -> 504;
            case "INTERNAL_ERROR" -> 500;
            default -> 400;
        };
        var node = JSON.createObjectNode().put("type","urn:clinical-intelligence:problem:"+code.toLowerCase(Locale.ROOT).replace('_','-'))
                .put("title",code).put("status",status).put("detail","La solicitud no pudo completarse.")
                .put("instance","urn:uuid:"+id).put("requestId",id.toString()).put("code",code);
        var errors = node.putArray("errors");
        if(pointer!=null) errors.addObject().put("pointer",pointer).put("code","INVALID_VALUE").put("message","Revisar el campo indicado.");
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store"); response.setHeader("X-Request-Id",id.toString());
        if(status==401) response.setHeader("WWW-Authenticate","ApiKey realm=\"clinical-intelligence\"");
        if(status==405) response.setHeader("Allow","POST");
        response.getOutputStream().write(JSON.writeValueAsBytes(node));
    }
}

