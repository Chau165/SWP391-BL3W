package mylib;

import java.util.regex.Pattern;

/**
 * ValidationUtil - Validation helpers for email, phone, etc.
 */
public class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private ValidationUtil() {}

    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validate phone format (simple check for 10-11 digits)
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        String clean = phone.trim().replaceAll("[^0-9]", "");
        return clean.length() >= 10 && clean.length() <= 11;
    }

    /**
     * Check if string is blank
     */
    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
