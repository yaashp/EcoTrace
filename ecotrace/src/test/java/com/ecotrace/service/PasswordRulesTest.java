package com.ecotrace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PasswordRulesTest {

    @Test
    void acceptsLettersAndDigits() {
        assertNull(PasswordRules.check("Recycle2025"));
    }

    @Test
    void rejectsShortPasswords() {
        assertNotNull(PasswordRules.check("abc123"));
    }

    @Test
    void requiresBothLettersAndDigits() {
        assertNotNull(PasswordRules.check("onlyletters"));
        assertNotNull(PasswordRules.check("12345678"));
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimit() {
        assertEquals("Use at most 72 characters", PasswordRules.check("a1".repeat(40)));
    }
}
