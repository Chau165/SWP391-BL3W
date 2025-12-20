package mylib;

/**
 * ========================================================================================================
 * UTILS: ValidationUtil - XÁC THỰC INPUT TỪ FRONTEND
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Validate tất cả input từ user trước khi xử lý
 * - Sử dụng Regex patterns để kiểm tra format
 * - Tránh SQL Injection, XSS, invalid data vào database
 * - Bảo mật application từ user input không hợp lệ
 * 
 * VALIDATION METHODS:
 * 1. isValidEmail(email): Kiểm tra email hợp lệ
 *    - Pattern: RFC 5322 simplified
 *    - Example: a@fpt.edu.vn, user.name+tag@example.com
 *    - Allow: letters, digits, +, &, *, -, _, .
 *    - Require: @ và domain với 2-7 chữ cái
 * 
 * 2. isValidVNPhone(phone): Kiểm tra số điện thoại Việt Nam
 *    - Pattern: (+84|84|0) + (3|5|7|8|9) + 8 chữ số
 *    - Valid: 0912345678, +84912345678, 84912345678
 *    - Prefixes: 03x, 05x, 07x, 08x, 09x (các đầu số Việt Nam)
 *    - Total: 10 hoặc 11 chữ số (tùy có +84 hay 0)
 * 
 * 3. isValidFullName(name): Kiểm tra họ tên
 *    - Pattern: 2-100 ký tự
 *    - Allow: chữ cái (có dấu tiếng Việt), khoảng trắng, dấu chấm, dấu gạch ngang, dấu nháy đơn
 *    - Example: Nguyễn Văn A, Trần Thị B, O'Connor, Mary-Jane
 *    - Regex: \p{L} = Unicode letters (support tiếng Việt)
 * 
 * 4. isValidPassword(password): Kiểm tra mật khẩu
 *    - Tối thiểu 6 ký tự
 *    - Phải có ít nhất 1 chữ cái (A-Z, a-z)
 *    - Phải có ít nhất 1 chữ số (0-9)
 *    - Allow: chữ cái, số, ký tự đặc biệt (@#$%^&+=!-)
 *    - Example: Pass123, Abc@123, MyP@ssw0rd
 * 
 * REGEX PATTERNS:
 * - EMAIL_PATTERN: ^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$
 * - PHONE_PATTERN: ^(\+84|84|0)(3|5|7|8|9)\d{8}$
 * - FULLNAME_PATTERN: ^[\p{L} .'-]{2,100}$
 * - PASSWORD_PATTERN: ^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@#$%^&+=!\-]{6,}$
 * 
 * TẠI SAO CẦN VALIDATION?
 * - Bảo mật: Tránh SQL Injection, XSS
 * - Data integrity: Chỉ lưu dữ liệu hợp lệ vào DB
 * - UX: Thông báo lỗi sớm, không chờ đến lúc insert DB
 * - Business logic: Email phải đúng để gửi OTP
 * 
 * LUỒNG SỬ DỤNG:
 * 1. FE gửi request với user input
 * 2. Controller parse JSON -> DTO object
 * 3. Controller gọi ValidationUtil.isValidXxx() cho mỗi field
 * 4. Nếu invalid: trả về 400 Bad Request + error message
 * 5. Nếu valid: tiếp tục xử lý
 * 
 * CLIENT-SIDE VS SERVER-SIDE VALIDATION:
 * - Client-side: Validate trước khi gửi (UX tốt, feedback nhanh)
 * - Server-side: Bắt buộc phải có (bảo mật, không tin tưởng FE)
 * - NEVER trust client input!
 * 
 * BEST PRACTICES:
 * - Validate sớm, fail fast
 * - Error message rõ ràng, giúp user hiểu lỗi
 * - Không tiết lộ thông tin hệ thống trong error message
 * - Sanitize input trước khi lưu DB (PreparedStatement giúp tránh SQL Injection)
 * 
 * SỬ DỤNG:
 * - Controller: RegisterSendOtpController, RegisterVerifyOtpController
 * - Controller: LoginController, ProfileController
 * - Anywhere cần validate user input
 */

import java.util.regex.Pattern;

public class ValidationUtil {

    // Email pattern provided by user
    public static final Pattern EMAIL_PATTERN = Pattern
            .compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

    // Vietnamese phone prefixes (common mobile prefixes). Accepts 10 or 11 digits
    // depending on prefix.
    // Example from user: "^(081|082|083|084|085|088|091|094)\\d{7}$" -> we'll
    // expand slightly to common prefixes.
    private static final String VN_PREFIXES = "^(03|05|07|08|09)"; // simple grouping for major prefixes
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+84|84|0)(3|5|7|8|9)\\d{8}$");

    // Full name: allow letters, spaces, accents, min 2 characters
    private static final Pattern FULLNAME_PATTERN = Pattern.compile("^[\\p{L} .'-]{2,100}$");

    // Password: min 6 chars, at least one digit and one letter. You can tighten if
    // desired.
    private static final Pattern PASSWORD_PATTERN = Pattern
            .compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@#$%^&+=!\\-]{6,}$");

    public static boolean isValidEmail(String email) {
        if (email == null)
            return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidVNPhone(String phone) {
        if (phone == null)
            return false;
        String p = phone.trim();
        return PHONE_PATTERN.matcher(p).matches();
    }

    public static boolean isValidFullName(String name) {
        if (name == null)
            return false;
        return FULLNAME_PATTERN.matcher(name.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        if (password == null)
            return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}