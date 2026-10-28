package com.clinicalintelligence.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class CredentialStore {
    public record Identity(String consumerId, Set<String> scopes) {
        public Identity { scopes = Set.copyOf(scopes); }
    }
    private record Verifier(byte[] digest, Identity identity) {}
    private final List<Verifier> verifiers;
    private CredentialStore(List<Verifier> verifiers) { this.verifiers = List.copyOf(verifiers); }

    public static CredentialStore parse(String json) {
        try {
            var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isArray() || root.isEmpty()) throw new IllegalArgumentException();
            var entries = new ArrayList<Verifier>();
            var hashes = new HashSet<String>();
            for (JsonNode node : root) {
                if (!node.isObject() || node.size() != 3 || !node.path("sha256").isTextual()
                        || !node.path("consumerId").isTextual() || !node.path("scopes").isArray()) throw new IllegalArgumentException();
                String hash = node.get("sha256").textValue();
                String consumer = node.get("consumerId").textValue();
                if (!hash.matches("[0-9a-f]{64}") || !hashes.add(hash)
                        || !consumer.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new IllegalArgumentException();
                var scopes = new HashSet<String>();
                for (JsonNode scope : node.get("scopes")) {
                    if (!scope.isTextual() || !scope.textValue().matches("[a-z][a-z0-9:._-]{0,127}")
                            || !scopes.add(scope.textValue())) throw new IllegalArgumentException();
                }
                entries.add(new Verifier(HexFormat.of().parseHex(hash), new Identity(consumer, scopes)));
            }
            return new CredentialStore(entries);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid credentials configuration");
        }
    }

    public Identity authenticate(String token) {
        if (token == null || !token.matches("ci_[A-Za-z0-9_-]{43}")) return null;
        byte[] supplied = sha256(token);
        Identity matched = null;
        for (Verifier verifier : verifiers)
            if (MessageDigest.isEqual(supplied, verifier.digest())) matched = verifier.identity();
        return matched;
    }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}

