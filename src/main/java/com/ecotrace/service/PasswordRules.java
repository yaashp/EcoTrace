package com.ecotrace.service;

/** Minimum password quality. Length is also capped at 72 because BCrypt ignores anything longer. */
public final class PasswordRules {

    private PasswordRules() {
    }

    /** Returns an error message, or null when the password is acceptable. */
    public static String check(String password) {
        if (password == null || password.length() < 8) {
            return "Use at least 8 characters";
        }
        if (password.length() > 72) {
            return "Use at most 72 characters";
        }
        boolean letter = false;
        boolean digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        if (!letter || !digit) {
            return "Include at least one letter and one number";
        }
        return null;
    }
}
