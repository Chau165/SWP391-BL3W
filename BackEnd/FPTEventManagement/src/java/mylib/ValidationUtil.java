package mylib;

import java.util.regex.Pattern;

/**
 * ValidationUtil — combined validators used by registerController.
 */
public final class ValidationUtil {

    private ValidationUtil() {}

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0(3|5|7|8|9)\\d{8}$");
    private static final Pattern FULLNAME_PATTERN = Pattern.compile("^[\\p{L} .'-]{2,100}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@#$%^&+=!\\-]{6,}$");

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidVNPhone(String phone) {
        if (phone == null) return false;
        String p = phone.replaceAll("\\s+", "").trim();
        if (p.startsWith("+84")) {
            p = "0" + p.substring(3);
        } else if (p.startsWith("84")) {
            p = "0" + p.substring(2);
        }
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

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
