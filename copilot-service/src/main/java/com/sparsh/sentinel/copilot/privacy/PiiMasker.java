package com.sparsh.sentinel.copilot.privacy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces personal data with stable tokens before text reaches a model (design decision §6).
 *
 * <p>A token is a keyed hash of the value - {@code PARTY-3f9a1c2e}, {@code IBAN-…}, {@code CARD-…},
 * {@code EMAIL-…} - so the same counterparty gets the same token in the case note, in every MCP
 * tool result and across calls, and a model can still reason that two transactions went to the
 * same party without ever learning who it is. Without the key the token cannot be reversed or
 * guessed from a list of names.
 *
 * <p>Names are masked because the caller says which strings are names (a counterparty field);
 * IBANs, card numbers and e-mail addresses are found by pattern wherever they appear, checksums
 * included, so a clause number or an amount is not mistaken for an account. Customer ids are not
 * masked: they are the bank's pseudonymous keys, the tools need them, and the customer's name is
 * never in anything the tools return.
 */
@Component
public class PiiMasker {

    private static final Logger log = LoggerFactory.getLogger(PiiMasker.class);

    static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b");
    static final Pattern CARD = Pattern.compile("\\b\\d(?:[ -]?\\d){12,18}\\b");
    static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");

    private final byte[] key;

    public PiiMasker(@Value("${sentinel.privacy.mask-key:}") String key) {
        if (key == null || key.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
            log.warn("sentinel.privacy.mask-key is not set: masking tokens are stable only until restart");
        } else {
            this.key = key.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** A fresh session: it remembers each token it hands out, so a model's reply can be unmasked. */
    public MaskingSession session() {
        return new MaskingSession(this);
    }

    /** The stable token for one value of one kind, e.g. ("PARTY", "Kessler Bau GmbH"). */
    String token(String kind, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((kind + ":" + normalise(value)).getBytes(StandardCharsets.UTF_8));
            return kind + "-" + HexFormat.of().formatHex(digest, 0, 4);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by the JDK specification", e);
        }
    }

    /**
     * Masks what can be found by pattern alone - IBANs, card numbers, e-mail addresses - for text
     * whose names are not known, such as an analyst's comment or a policy question.
     */
    public String maskPatterns(String text) {
        return session().mask(text);
    }

    /** Applies the three patterns, handing each match to {@code replace}. */
    String replacePatterns(String text, java.util.function.BiFunction<String, String, String> replace) {
        String out = replaceAll(IBAN, text, m -> validIban(m) ? replace.apply("IBAN", m) : m);
        out = replaceAll(CARD, out, m -> luhn(m) ? replace.apply("CARD", m) : m);
        return replaceAll(EMAIL, out, m -> replace.apply("EMAIL", m));
    }

    private static String replaceAll(Pattern pattern, String text, java.util.function.UnaryOperator<String> replace) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(replace.apply(matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** ISO 13616 check: move the first four characters to the end, letters to numbers, mod 97 == 1. */
    static boolean validIban(String candidate) {
        String iban = candidate.replace(" ", "");
        if (iban.length() < 15 || iban.length() > 34) {
            return false;
        }
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /** The card-number checksum, so an amount or a reference with 13+ digits is left alone. */
    static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("[ -]", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }

    private static String normalise(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
