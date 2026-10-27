package com.ascenddevs.safetyapp.service;

import java.util.regex.Pattern;

/** Pure sign-up rules (no Spring, no database) so they can be unit-tested directly. */
public final class AuthValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private AuthValidator() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** Returns an error message, or null when the sign-up details are acceptable. */
    public static String validateSignUp(String name, String email, String password) {
        if (name == null || name.isBlank()) return "Name is required.";
        if (name.trim().length() > 100) return "Name is too long.";
        if (email == null || !EMAIL.matcher(email.trim()).matches()) return "Enter a valid email address.";
        return validatePassword(password);
    }

    public static String validatePassword(String password) {
        if (password == null || password.length() < 8) return "Password must be at least 8 characters.";
        if (password.length() > 72) return "Password must be at most 72 characters.";
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) return "Password must contain both letters and numbers.";
        return null;
    }
}
