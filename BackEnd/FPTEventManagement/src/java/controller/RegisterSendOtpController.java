package controller;

/**
 * ========================================================================================================
 * CONTROLLER: RegisterSendOtpController - GỬI MÃ OTP ĐĂNG KÝ TÀI KHOẢN
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Nhận thông tin đăng ký từ Frontend (fullName, phone, email, password)
 * - Validate tất cả các field (regex, format, length...)
 * - Kiểm tra email đã tồn tại chưa (tránh duplicate)
 * - Sinh mã OTP 6 chữ số ngẫu nhiên
 * - Lưu thông tin tạm thời vào OtpCache (in-memory cache)
 * - Gửi OTP qua email
 * - Trả về response cho FE
 * 
 * ENDPOINT: POST /api/register/send-otp
 * 
 * REQUEST BODY:
 * {
 *   "fullName": "Nguyễn Văn A",
 *   "phone": "0912345678",
 *   "email": "example@fpt.edu.vn",
 *   "password": "Pass123"
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "otp_sent",
 *   "message": "OTP has been sent to your email"
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Invalid input, validation failed
 * - 409 Conflict: Email already exists
 * - 502 Bad Gateway: Failed to send email
 * 
 * VALIDATION RULES:
 * - fullName: Ít nhất 2 ký tự, cho phép chữ cái, dấu cách, dấu tiếng Việt
 * - phone: Format số điện thoại Việt Nam (03x, 05x, 07x, 08x, 09x) + 8 chữ số
 * - email: Format email chuẩn RFC 5322
 * - password: Tối thiểu 6 ký tự, phải có cả chữ và số
 * 
 * LUỒNG XỬ LÝ:
 * 1. FE gửi POST request với thông tin đăng ký
 * 2. Backend validate từng field (ValidationUtil)
 * 3. Check email đã tồn tại chưa (UsersDAO.existsByEmail)
 * 4. Sinh OTP 6 chữ số (EmailService.generateOtp)
 * 5. Lưu vào OtpCache (5 phút timeout)
 * 6. Gửi email OTP (EmailService.sendRegistrationOtpEmail)
 * 7. Trả về success/error response
 * 8. FE chuyển sang màn hình nhập OTP
 * 
 * OTP CACHE:
 * - Key: email
 * - Value: PendingUser(fullName, phone, email, password, otp, expiresAt, attempts)
 * - TTL: 5 phút
 * - Max attempts: 5 lần nhập sai
 * - Resend limit: 5 lần gửi lại
 * 
 * SECURITY:
 * - Password chưa hash tại đây (hash khi verify OTP thành công)
 * - CORS whitelist: localhost:3000, localhost:5173, ngrok
 * - Rate limiting: Nên thêm để tránh spam OTP
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/UsersDAO.java (check email exists)
 * - Utils: mylib/ValidationUtil.java (validate input)
 * - Service: mylib/EmailService.java (send OTP email)
 * - Cache: mylib/OtpCache.java (store temporary data)
 * - Next step: controller/RegisterVerifyOtpController.java (verify OTP)
 */

import DAO.UsersDAO;
import com.google.gson.Gson;
import mylib.EmailService;
import mylib.OtpCache;
import mylib.ValidationUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register/send-otp")
public class RegisterSendOtpController extends HttpServlet {

    // Gson để parse JSON request body và tạo JSON response
    private final Gson gson = new Gson();

    /**
     * ================================================================================================
     * INNER CLASS: RegisterRequest - DTO CHO REQUEST BODY
     * ================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Ánh xạ JSON request body từ FE sang Java object
     * - Gson tự động parse JSON -> RegisterRequest object
     * 
     * JSON EXAMPLE:
     * {
     * "fullName": "Nguyễn Văn A",
     * "phone": "0912345678",
     * "email": "a@fpt.edu.vn",
     * "password": "Pass123"
     * }
     */
    static class RegisterRequest {

        String fullName;
        String phone;
        String email;
        String password;
    }

    /**
     * ================================================================================================
     * METHOD: doOptions - XỬ LÝ PREFLIGHT REQUEST (CORS)
     * ================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Xử lý OPTIONS request từ browser trước khi gửi POST thực sự
     * - Thiết lập CORS headers để cho phép cross-origin requests
     * - Browser tự động gửi OPTIONS khi POST từ domain khác
     * 
     * FLOW:
     * 1. Browser phát hiện request từ localhost:3000 -> backend
     * 2. Browser tự động gửi OPTIONS request (preflight)
     * 3. Server trả về 204 No Content + CORS headers
     * 4. Browser cho phép POST request thực sự được gửi
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204 No Content
    }

    /**
     * ================================================================================================
     * METHOD: doPost - GỬI MÃ OTP QUA EMAIL
     * ================================================================================================
     * 
     * ENDPOINT: POST /api/register/send-otp
     * CONTENT-TYPE: application/json
     * 
     * REQUEST BODY:
     * {
     * "fullName": "Nguyễn Văn A",
     * "phone": "0912345678",
     * "email": "a@fpt.edu.vn",
     * "password": "Pass123"
     * }
     * 
     * RESPONSE SUCCESS (200):
     * {
     * "status": "otp_sent",
     * "message": "OTP has been sent to your email"
     * }
     * 
     * RESPONSE ERROR:
     * - 400: {"error": "Full name is invalid"}
     * - 400: {"error": "Phone number is invalid"}
     * - 400: {"error": "Email is invalid"}
     * - 400: {"error": "Password must be at least 6 characters, include letters and
     * digits"}
     * - 409: {"error": "Email already exists"}
     * - 502: {"error": "Failed to send OTP email"}
     * 
     * VALIDATION FLOW:
     * 1. Parse JSON request -> RegisterRequest object
     * 2. Validate fullName (ValidationUtil.isValidFullName)
     * 3. Validate phone (ValidationUtil.isValidVNPhone)
     * 4. Validate email (ValidationUtil.isValidEmail)
     * 5. Validate password (ValidationUtil.isValidPassword)
     * 6. Check email exists (UsersDAO.existsByEmail)
     * 7. Generate OTP 6 digits
     * 8. Save to OtpCache (5 min TTL)
     * 9. Send email via SMTP
     * 10. Return success response
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        setCorsHeaders(resp, req); // Thiết lập CORS headers
        resp.setContentType("application/json;charset=UTF-8"); // Response sẽ là JSON UTF-8
        req.setCharacterEncoding("UTF-8"); // Đọc request body với encoding UTF-8

        try (BufferedReader reader = req.getReader(); PrintWriter out = resp.getWriter()) {

            // Parse JSON request body -> RegisterRequest object
            RegisterRequest input = gson.fromJson(reader, RegisterRequest.class);
            if (input == null) {
                resp.setStatus(400); // Bad Request
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            // ===== VALIDATION: Full Name =====
            // Kiểm tra họ tên hợp lệ (ít nhất 2 ký tự, cho phép chữ cái + dấu tiếng Việt)
            if (!ValidationUtil.isValidFullName(input.fullName)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Full name is invalid\"}");
                return;
            }

            // ===== VALIDATION: Phone =====
            // Kiểm tra số điện thoại Việt Nam (03x, 05x, 07x, 08x, 09x + 8 chữ số)
            if (!ValidationUtil.isValidVNPhone(input.phone)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Phone number is invalid\"}");
                return;
            }

            // ===== VALIDATION: Email =====
            // Kiểm tra email format chuẩn RFC 5322
            if (!ValidationUtil.isValidEmail(input.email)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Email is invalid\"}");
                return;
            }

            // ===== VALIDATION: Password =====
            // Kiểm tra mật khẩu (min 6 ký tự, có cả chữ và số)
            if (!ValidationUtil.isValidPassword(input.password)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Password must be at least 6 characters, include letters and digits\"}");
                return;
            }

            // ===== CHECK DUPLICATE EMAIL =====
            // Kiểm tra email đã tồn tại trong database chưa
            UsersDAO dao = new UsersDAO();
            if (dao.existsByEmail(input.email)) {
                resp.setStatus(409); // 409 Conflict
                out.print("{\"error\":\"Email already exists\"}");
                return;
            }

            // ===== GENERATE OTP =====
            // Sinh mã OTP 6 chữ số ngẫu nhiên (100000 - 999999)
            String otp = EmailService.generateOtp();

            // ===== SAVE TO CACHE =====
            // Lưu thông tin tạm thời vào OtpCache (in-memory)
            // TTL: 5 phút, sau đó tự động xóa
            OtpCache.put(input.email, input.fullName, input.phone, input.password, otp);
            System.out.println("[send-otp] ✅ Generated OTP " + otp + " for " + input.email);

            // ===== SEND EMAIL =====
            // Gửi OTP qua email (SMTP Gmail)
            boolean sent = EmailService.sendRegistrationOtpEmail(input.email, otp);
            if (!sent) {
                resp.setStatus(502); // 502 Bad Gateway (SMTP server error)
                out.print("{\"error\":\"Failed to send OTP email\"}");
                return;
            }

            // ===== SUCCESS RESPONSE =====
            resp.setStatus(200);
            out.print("{\"status\":\"otp_sent\",\"message\":\"OTP has been sent to your email\"}");

        } catch (Exception e) {
            // Log exception để debug
            e.printStackTrace();
            resp.setStatus(500); // Internal Server Error
            resp.getWriter().print("{\"error\":\"Unhandled error in send-otp\"}");
        }
    }

    /**
     * ================================================================================================
     * METHOD: setCorsHeaders - THIẾT LẬP CORS HEADERS
     * ================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Cho phép FE (React/Vue) từ domain khác gọi API
     * - Whitelist các origin được phép: localhost, ngrok
     * - Thiết lập các header cần thiết cho CORS
     * 
     * CORS HEADERS:
     * - Access-Control-Allow-Origin: Domain được phép
     * - Access-Control-Allow-Credentials: Cho phép gửi cookies
     * - Access-Control-Allow-Methods: GET, POST, OPTIONS
     * - Access-Control-Allow-Headers: Content-Type, Authorization
     * - Access-Control-Max-Age: Cache preflight 24h
     * 
     * WHITELISTED ORIGINS:
     * - http://localhost:5173 (Vite)
     * - http://localhost:3000 (Create React App)
     * - *.ngrok-free.app (Ngrok tunneling)
     */
    // ================= CORS =================
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        // Lấy Origin header từ request (domain của FE)
        String origin = req.getHeader("Origin");

        // Kiểm tra origin có trong whitelist không
        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || // ⭐ Cho phép ngrok
                origin.contains("ngrok.app") // ⭐ (phòng trường hợp domain mới)
        );

        if (allowed) {
            // Cho phép origin này gọi API
            res.setHeader("Access-Control-Allow-Origin", origin);
            // Cho phép gửi credentials (cookies, JWT token)
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // Block origin không trong whitelist
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Header Vary: Origin -> báo browser/proxy biết response phụ thuộc origin
        res.setHeader("Vary", "Origin");

        // Cho phép các HTTP methods
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        // Cho phép FE gửi các headers này
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");

        // Cho phép FE đọc Authorization header từ response
        res.setHeader("Access-Control-Expose-Headers", "Authorization");

        // Cache preflight request 24h (86400 seconds)
        res.setHeader("Access-Control-Max-Age", "86400");
    }

}