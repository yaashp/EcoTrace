package com.ecotrace.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HandoverRulesTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void codesAreSixDigitsAndZeroPadded() {
        for (int i = 0; i < 2_000; i++) {
            String code = HandoverRules.newCode(random);
            assertEquals(6, code.length());
            assertTrue(HandoverRules.isWellFormedCode(code), code);
        }
    }

    @Test
    void wellFormedCodeRejectsEverythingElse() {
        assertTrue(HandoverRules.isWellFormedCode("000000"));
        assertFalse(HandoverRules.isWellFormedCode(null));
        assertFalse(HandoverRules.isWellFormedCode("12345"));
        assertFalse(HandoverRules.isWellFormedCode("1234567"));
        assertFalse(HandoverRules.isWellFormedCode("12ab56"));
        assertFalse(HandoverRules.isWellFormedCode(" 123456"));
    }

    @Test
    void handoverReferencesHaveAPrefixAndDoNotRepeatInPractice() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String ref = HandoverRules.newReference(random);
            assertTrue(ref.matches("HO-[A-HJ-NP-Z2-9]{8}"), ref);
            seen.add(ref);
        }
        assertEquals(1_000, seen.size());
    }

    @Test
    void photoTypeComesFromSignatureNotFromClaimedName() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("image/jpeg", HandoverRules.detectImageType(jpeg));
        assertEquals("image/png", HandoverRules.detectImageType(png));
        assertEquals("image/webp", HandoverRules.detectImageType(webp));
    }

    @Test
    void scriptsMarkupAndShortFilesAreNotImages() {
        byte[] html = "<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);
        byte[] gif = "GIF89a....".getBytes(StandardCharsets.ISO_8859_1);
        assertNull(HandoverRules.detectImageType(html));
        assertNull(HandoverRules.detectImageType(svg));
        assertNull(HandoverRules.detectImageType(gif));
        assertNull(HandoverRules.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8}));
        assertNull(HandoverRules.detectImageType(null));
    }

    @Test
    void codeIsUsableOnlyBeforeExpiryAndWithinTheAttemptLimit() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        HandoverCode code = new HandoverCode(null, null, "hash", now.plusSeconds(60), now);
        assertTrue(code.isUsable(now));
        assertFalse(code.isUsable(now.plusSeconds(60)), "expiry instant is already too late");
        assertEquals(HandoverRules.MAX_ATTEMPTS, code.attemptsLeft());

        for (int i = 0; i < HandoverRules.MAX_ATTEMPTS; i++) {
            code.recordFailure();
        }
        assertFalse(code.isUsable(now), "attempt limit ends the code");
        assertEquals(0, code.attemptsLeft());
    }

    @Test
    void usedOrRevokedCodesCannotBeUsedAgain() {
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        HandoverCode used = new HandoverCode(null, null, "hash", now.plusSeconds(600), now);
        used.markUsed(now);
        assertFalse(used.isUsable(now));

        HandoverCode revoked = new HandoverCode(null, null, "hash", now.plusSeconds(600), now);
        revoked.revoke(now);
        assertFalse(revoked.isUsable(now));
    }
}
