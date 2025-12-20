package DTO;

/**
 * ========================================================================================================
 * DTO: RegisterRequest - REQUEST BODY CHO API ĐĂNG KÝ TÀI KHOẢN
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Class này dùng để nhận dữ liệu từ Frontend khi user đăng ký tài khoản
 * - Gson tự động parse JSON request body -> RegisterRequest object
 * - Chứa các field cần thiết để tạo tài khoản mới
 * 
 * JSON REQUEST EXAMPLE:
 * {
 * "fullName": "Nguyễn Văn A",
 * "phone": "0912345678",
 * "email": "a@fpt.edu.vn",
 * "password": "Pass123",
 * "recaptchaToken": "03AGdBq25..." (optional, nếu có reCAPTCHA)
 * }
 * 
 * FIELDS:
 * - fullName: Họ và tên đầy đủ (tối thiểu 2 ký tự, cho phép tiếng Việt)
 * - phone: Số điện thoại Việt Nam (03x, 05x, 07x, 08x, 09x)
 * - email: Email hợp lệ (RFC 5322 format)
 * - password: Mật khẩu (tối thiểu 6 ký tự, có cả chữ và số)
 * - recaptchaToken: Token từ Google reCAPTCHA (để chống bot)
 * 
 * VALIDATION:
 * - Được thực hiện ở Controller bằng ValidationUtil
 * - fullName: ValidationUtil.isValidFullName()
 * - phone: ValidationUtil.isValidVNPhone()
 * - email: ValidationUtil.isValidEmail()
 * - password: ValidationUtil.isValidPassword()
 * - recaptchaToken: RecaptchaUtils.verify()
 * 
 * LUỒNG SỬ DỤNG:
 * 1. Frontend gửi POST /api/register/send-otp với JSON body
 * 2. Gson.fromJson(reader, RegisterRequest.class)
 * 3. Controller validate từng field
 * 4. Nếu hợp lệ: sinh OTP và lưu vào OtpCache
 * 5. Gửi OTP qua email
 * 
 * SECURITY:
 * - Password được gửi dạng plain text qua HTTPS
 * - Backend hash ngay khi tạo user (không lưu plain)
 * - recaptchaToken verify với Google để chống bot
 * 
 * SỬ DỤNG:
 * - Controller: RegisterSendOtpController.java (parse request body)
 * - Validation: mylib/ValidationUtil.java
 * - Related: DTO/Users.java (entity sau khi verify OTP)
 */

public class RegisterRequest {

    private String fullName;
    private String phone;
    private String email;
    private String password;
    private String recaptchaToken;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRecaptchaToken() {
        return recaptchaToken;
    }

    public void setRecaptchaToken(String recaptchaToken) {
        this.recaptchaToken = recaptchaToken;
    }
}