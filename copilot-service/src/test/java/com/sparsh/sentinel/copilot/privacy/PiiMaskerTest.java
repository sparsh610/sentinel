package com.sparsh.sentinel.copilot.privacy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker("test-key");

    @Test
    void aRegisteredNameIsMaskedWhereverItAppearsAndComesBack() {
        MaskingSession session = masker.session();
        String token = session.party("Kessler Bau GmbH");

        String masked = session.mask("Three deposits from Kessler Bau GmbH; Kessler Bau GmbH is a customer.");

        assertThat(masked).doesNotContain("Kessler").contains(token);
        assertThat(session.unmask("Summary: " + token + " structured the deposits."))
                .isEqualTo("Summary: Kessler Bau GmbH structured the deposits.");
    }

    @Test
    void tokensAreStableAcrossSessionsAndDifferAcrossNames() {
        assertThat(masker.session().party("Kessler Bau GmbH")).isEqualTo(masker.session().party("kessler  bau gmbh"));
        assertThat(masker.session().party("Kessler Bau GmbH")).isNotEqualTo(masker.session().party("Cafe Morgenrot"));
        // A different key, a different token: without the key a token says nothing about the name.
        assertThat(new PiiMasker("other-key").session().party("Kessler Bau GmbH"))
                .isNotEqualTo(masker.session().party("Kessler Bau GmbH"));
    }

    @Test
    void ibansCardNumbersAndEmailsAreFoundByPatternAndChecksum() {
        String text = "Paid from DE89 3704 0044 0532 0130 00 with card 4111 1111 1111 1111, contact a.weber@example.de";

        String masked = masker.maskPatterns(text);

        assertThat(masked).doesNotContain("DE89", "4111", "a.weber").contains("IBAN-", "CARD-", "EMAIL-");
    }

    @Test
    void clauseNumbersAmountsAndNumbersThatFailTheChecksumAreLeftAlone() {
        String text = "AML-04.2 applies to EUR 9500.00 booked 2026-10-14T09:12:00Z, ref 1234 5678 9012 3456,"
                + " account DE00 1234 5678 9012 3456 78";

        assertThat(masker.maskPatterns(text)).isEqualTo(text);
    }
}
