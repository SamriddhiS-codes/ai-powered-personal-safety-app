package com.ascenddevs.safetyapp.service;

import java.util.regex.Pattern;

/**
 * Pure validation rules for trusted contacts, kept free of Spring and the database
 * so they can be unit-tested directly.
 */
public final class ContactValidator {

    public static final int MAX_CONTACTS = 5;

    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ContactValidator() {
    }

    /** Removes spaces, dashes and brackets from a phone number: "+91 98765-43210" becomes "+919876543210". */
    public static String cleanPhone(String phone) {
        return phone == null ? null : phone.replaceAll("[\\s\\-()]", "");
    }

    /** Returns an error message, or null when the contact is acceptable. */
    public static String validate(String name, String phone, String email) {
        if (name == null || name.isBlank()) {
            return "Name is required.";
        }
        if (name.trim().length() > 100) {
            return "Name is too long.";
        }
        boolean hasPhone = phone != null && !phone.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        if (!hasPhone && !hasEmail) {
            return "Give a phone number or an email so the contact can be reached.";
        }
        if (hasPhone && !PHONE.matcher(cleanPhone(phone)).matches()) {
            return "Phone must be 7 to 15 digits, with an optional leading +.";
        }
        if (hasEmail && !EMAIL.matcher(email.trim()).matches()) {
            return "Email address is not valid.";
        }
        return null;
    }
}
