package com.sparsh.sentinel.copilot.privacy;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One piece of model-bound work - an investigation's prompt, an MCP tool result, a policy
 * question. It masks the text going out and remembers every token it handed out, so the reply
 * can be unmasked for the analyst, who is entitled to see the names.
 *
 * <p>Not thread-safe; one per call.
 */
public final class MaskingSession {

    private final PiiMasker masker;
    /** Token -> the value it stands for. */
    private final Map<String, String> originals = new LinkedHashMap<>();
    /** Names registered for masking -> their tokens. */
    private final Map<String, String> names = new LinkedHashMap<>();

    MaskingSession(PiiMasker masker) {
        this.masker = masker;
    }

    /** Registers a counterparty name and returns its token; masks every later mention of it too. */
    public String party(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return names.computeIfAbsent(name.strip(), n -> remember(masker.token("PARTY", n), n));
    }

    /** Masks registered names (longest first, so "Kessler Bau GmbH" goes before "Kessler"), then patterns. */
    public String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> name : names.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
                .toList()) {
            out = out.replace(name.getKey(), name.getValue());
        }
        return masker.replacePatterns(out, (kind, value) -> remember(masker.token(kind, value), value));
    }

    /** Puts the originals back - for the analyst's copy of what the model wrote. */
    public String unmask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> token : originals.entrySet()) {
            out = out.replace(token.getKey(), token.getValue());
        }
        return out;
    }

    /** How many distinct values were masked - for the audit line, never the values themselves. */
    public int maskedCount() {
        return originals.size();
    }

    private String remember(String token, String original) {
        originals.putIfAbsent(token, original);
        return token;
    }
}
