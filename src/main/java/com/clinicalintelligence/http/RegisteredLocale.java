package com.clinicalintelligence.http;

import com.clinicalintelligence.extraction.application.ExtractionException;
import static com.clinicalintelligence.extraction.application.ExtractionException.Code.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline IANA validity checks, followed by product language support. */
public final class RegisteredLocale {
    private static final Map<String, Map<String, String>> REGISTRY = load();

    public String validate(String value) {
        if (value == null) return "es";
        String tag = value.toLowerCase(Locale.ROOT);
        try {
            new Locale.Builder().setLanguageTag(tag).build();
            String[] parts = tag.split("-", -1);
            if (parts[0].length() < 2 || parts[0].length() > 8) invalid();
            if (!contains("grandfathered", tag) && !contains("redundant", tag)) validateParts(parts);
        } catch (IllformedLocaleException malformed) { invalid(); }
        String primary = tag.split("-", 2)[0];
        if (!primary.equals("es")) throw new ExtractionException(UNSUPPORTED_LOCALE);
        return value;
    }

    private static void validateParts(String[] parts) {
        if (!contains("language", parts[0])) invalid();
        int i = 1, extlangs = 0;
        while (i < parts.length && parts[i].length() == 3 && letters(parts[i])) {
            String prefix = REGISTRY.getOrDefault("extlang", Map.of()).get(parts[i]);
            if (prefix == null || !Arrays.asList(prefix.split(",")).contains(String.join("-", Arrays.copyOf(parts, i)))) invalid();
            i++; if (++extlangs > 3) invalid();
        }
        if (i < parts.length && parts[i].length() == 4 && letters(parts[i])) {
            if (!contains("script", parts[i++])) invalid();
        }
        if (i < parts.length && (parts[i].length() == 2 || parts[i].length() == 3 && digits(parts[i]))) {
            if (!contains("region", parts[i++])) invalid();
        }
        var variants = new HashSet<String>();
        while (i < parts.length && parts[i].length() > 1) {
            if (!contains("variant", parts[i]) || !variants.add(parts[i])) invalid();
            i++;
        }
        var extensions = new HashSet<String>();
        while (i < parts.length) {
            String singleton = parts[i++];
            if (singleton.equals("x")) {
                if (i == parts.length) invalid();
                return;
            }
            if (!Set.of("u", "t").contains(singleton) || !extensions.add(singleton)) invalid();
            int start = i;
            while (i < parts.length && parts[i].length() > 1) i++;
            if (start == i) invalid();
        }
    }

    private static boolean letters(String value) { return value.chars().allMatch(c -> c >= 'a' && c <= 'z'); }
    private static boolean digits(String value) { return value.chars().allMatch(c -> c >= '0' && c <= '9'); }
    private static void invalid() { throw new ExtractionException(INVALID_LOCALE); }

    private static boolean contains(String type, String subtag) {
        Map<String, String> entries = REGISTRY.getOrDefault(type, Map.of());
        if (entries.containsKey(subtag)) return true;
        return entries.keySet().stream().filter(key -> key.contains("..")).anyMatch(key -> {
            String[] bounds = key.split("\\.\\.");
            return subtag.length() == bounds[0].length() && subtag.compareTo(bounds[0]) >= 0 && subtag.compareTo(bounds[1]) <= 0;
        });
    }

    private static Map<String, Map<String, String>> load() {
        try (var stream = RegisteredLocale.class.getResourceAsStream("/bcp47/registry.txt")) {
            if (stream == null) throw new IOException();
            var result = new HashMap<String, Map<String, String>>();
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] fields = line.split("\\|", -1);
                if (fields.length != 3) throw new IOException();
                result.computeIfAbsent(fields[0], ignored -> new HashMap<>()).put(fields[1], fields[2]);
            }
            var frozen = new HashMap<String, Map<String, String>>();
            result.forEach((key, values) -> frozen.put(key, Map.copyOf(values)));
            return Map.copyOf(frozen);
        } catch (IOException invalid) { throw new IllegalStateException("BCP47 registry unavailable"); }
    }
}

