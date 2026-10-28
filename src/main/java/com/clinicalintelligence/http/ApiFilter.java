package com.clinicalintelligence.http;

import com.clinicalintelligence.extraction.application.ExtractionException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.MediaType;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ApiFilter extends OncePerRequestFilter {
    static final String ID = ApiFilter.class.getName()+".id";
    static final String BODY = ApiFilter.class.getName()+".body";
    static final String IDENTITY = ApiFilter.class.getName()+".identity";
    private final CredentialStore credentials;
    private final int bodyBytes;
    ApiFilter(CredentialStore credentials,int bodyBytes) { this.credentials=credentials; this.bodyBytes=bodyBytes; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException {
        UUID id = UUID.randomUUID();
        request.setAttribute(ID,id);
        response.setHeader("X-Request-Id",id.toString()); response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Content-Type-Options","nosniff");
        try {
            if(!request.isSecure() && !InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress())
                throw new HttpFailure("INVALID_REQUEST");
            List<String> keys = Collections.list(request.getHeaders("X-API-Key"));
            var identity = keys.size()==1 ? credentials.authenticate(keys.getFirst()) : null;
            if(identity==null) throw new HttpFailure("INVALID_API_KEY");
            if(!identity.scopes().contains("extractions:write")) throw new HttpFailure("INSUFFICIENT_SCOPE");
            request.setAttribute(IDENTITY,identity);
            if(!"/api/v1/extractions".equals(request.getRequestURI())) throw new HttpFailure("INVALID_REQUEST");
            if(!"POST".equals(request.getMethod())) throw new HttpFailure("METHOD_NOT_ALLOWED");
            if(!acceptable(Collections.list(request.getHeaders("Accept")))) throw new HttpFailure("NOT_ACCEPTABLE");
            List<String> encodings=Collections.list(request.getHeaders("Content-Encoding"));
            if(!encodings.isEmpty() && (encodings.size()!=1 || !"identity".equalsIgnoreCase(encodings.getFirst().trim())))
                throw new HttpFailure("UNSUPPORTED_CONTENT_ENCODING");
            List<String> contentTypes=Collections.list(request.getHeaders("Content-Type"));
            if(contentTypes.size()!=1 || !jsonUtf8(contentTypes.getFirst())) throw new HttpFailure("UNSUPPORTED_MEDIA_TYPE");
            if(request.getContentLengthLong()>bodyBytes) throw new HttpFailure("INPUT_LIMIT_EXCEEDED");
            var bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];
            try(var stream=request.getInputStream()) {
                int count;
                while((count=stream.read(buffer,0,Math.min(buffer.length,bodyBytes-bytes.size()+1)))!=-1) {
                    if(bytes.size()+count>bodyBytes) throw new HttpFailure("INPUT_LIMIT_EXCEEDED");
                    bytes.write(buffer,0,count);
                }
            }
            request.setAttribute(BODY,bytes.toByteArray());
            chain.doFilter(request,response);
        } catch(HttpFailure failure) {
            Problems.write(response,id,failure.code(),failure.pointer());
        } catch(ExtractionException failure) {
            Problems.write(response,id,failure.code().name(),null);
        } catch(Exception unexpected) {
            if(!response.isCommitted()) {
                response.resetBuffer();
                Problems.write(response,id,"INTERNAL_ERROR",null);
            }
        }
    }

    private static boolean jsonUtf8(String raw) {
        try {
            MediaType type=MediaType.parseMediaType(raw);
            return "application".equalsIgnoreCase(type.getType()) && "json".equalsIgnoreCase(type.getSubtype())
                    && (type.getCharset()==null || StandardCharsets.UTF_8.equals(type.getCharset()));
        } catch(Exception invalid) { return false; }
    }
    private static boolean acceptable(List<String> values) {
        if(values.isEmpty()) return true;
        try {
            var ranges=MediaType.parseMediaTypes(String.join(",",values));
            MediaType target=MediaType.APPLICATION_JSON;
            double quality=0; int best=-1;
            for(MediaType range:ranges) if(range.isCompatibleWith(target)) {
                int specificity=(range.isWildcardType()?0:2)+(range.isWildcardSubtype()?0:1);
                if(specificity>best) { best=specificity; quality=range.getQualityValue(); }
                else if(specificity==best) quality=Math.max(quality,range.getQualityValue());
            }
            return quality>0;
        } catch(Exception invalid) { return false; }
    }
}

