package mylib;

import java.util.regex.Pattern;

public class ValidationUtil {

    // Email pattern provided by user
    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    // Vietnamese phone prefixes (common mobile prefixes). Accepts 10 or 11 digits depending on prefix.
    // Example from user: "^(081|082|083|084|085|088|091|094)\\d{7}$" -> we'll expand slightly to common prefixes.
    private static final String VN_PREFIXES = "^(03|05|07|08|09)"; // simple grouping for major prefixes
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+84|84|0)(3|5|7|8|9)\\d{8}$");

    // Full name: allow letters, spaces, accents, min 2 characters
    private static final Pattern FULLNAME_PATTERN = Pattern.compile("^[\\p{L} .'-]{2,100}$");

    // Password: min 6 chars, at least one digit and one letter. You can tighten if desired.
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@#$%^&+=!\\-]{6,}$");

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidVNPhone(String phone) {
        if (phone == null) return false;
        String p = phone.trim();
        return PHONE_PATTERN.matcher(p).matches();
    }

    public static boolean isValidFullName(String name) {
        if (name == null) return false;
        return FULLNAME_PATTERN.matcher(name.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}