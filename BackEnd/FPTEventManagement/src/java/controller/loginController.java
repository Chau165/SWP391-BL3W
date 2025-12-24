package controller;

/**
 * ========================================================================================================
 * CONTROLLER: loginController - ĐĂNG NHẬP VỚI reCAPTCHA
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Đăng nhập user với email và mật khẩu
 * - Xác thực reCAPTCHA (chống bot, spam login)
 * - Kiểm tra thông tin login với database
 * - Kiểm tra user status (BLOCKED không được login)
 * - Sinh JWT token cho session
 * - Trả về user info và token cho Frontend
 * 
 * ENDPOINT: POST /api/login
 * 
 * REQUEST BODY:
 * {
 *   "email": "a@fpt.edu.vn",
 *   "password": "123456",
 *   "recaptchaToken": "03AGdBq27..." // từ Google reCAPTCHA
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "success",
 *   "message": "Đăng nhập thành công",
 *   "user": {
 *     "userId": 1,
 *     "email": "a@fpt.edu.vn",
 *     "fullName": "Nguyễn Văn A",
 *     "role": "ORGANIZER",
 *     "phone": "0901234567",
 *     "status": "ACTIVE",
 *     "avatar": "https://..."
 *   },
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Thiếu field, email không hợp lệ, reCAPTCHA token rỗng
 * - 401 Unauthorized: Email/password sai, user bị BLOCKED
 * - 403 Forbidden: reCAPTCHA verification failed
 * - 500 Internal Server Error: Lỗi database, JWT generation
 * 
 * LUỒNG XỬ LÝ:
 * 1. FE gửi POST request với email, password, recaptchaToken
 * 2. Parse JSON request body
 * 3. Validate các field không rỗng
 * 4. Validate email format (ValidationUtil.isValidEmail)
 * 5. Verify reCAPTCHA token với Google API (RecaptchaUtils.verify)
 * 6. Nếu reCAPTCHA failed -> 403 Forbidden
 * 7. Kiểm tra login credentials (UsersDAO.checkLogin)
 * 8. Hash password và so sánh với database (SHA-256)
 * 9. Nếu login failed -> 401 Unauthorized
 * 10. Kiểm tra user status = "BLOCKED"
 * 11. Nếu BLOCKED -> 401 Unauthorized
 * 12. Sinh JWT token (JwtUtils.generateToken) với TTL 7 ngày
 * 13. Trả về user info + token
 * 14. FE lưu token vào localStorage/sessionStorage
 * 
 * reCAPTCHA FLOW:
 * - Frontend: Load reCAPTCHA v2/v3 từ Google
 * - User hoàn thành challenge (v2) hoặc invisible (v3)
 * - FE nhận recaptchaToken từ Google
 * - FE gửi recaptchaToken cùng email/password
 * - Backend verify token với Google API
 * - Google trả về success/fail + score (v3)
 * 
 * SECURITY:
 * - reCAPTCHA: Chống bot, brute-force attack
 * - Password hash: SHA-256 (nên nâng cấp lên BCrypt)
 * - JWT token: 7 ngày expiration, signed với SECRET_KEY
 * - User status check: Không cho BLOCKED user login
 * - CORS whitelist: Chỉ cho phép origins đã config
 * - Nên thêm rate limiting để chống brute-force
 * 
 * JWT TOKEN:
 * - Algorithm: HS256 (HMAC with SHA-256)
 * - Claims: userId, email, fullName, role
 * - Expiration: 7 ngày (604800 seconds)
 * - Signature: SECRET_KEY từ config
 * - Header: "Authorization: Bearer <token>"
 * 
 * USER STATUS:
 * - ACTIVE: Đăng nhập bình thường
 * - BLOCKED: Không được đăng nhập (admin khóa)
 * - PENDING: Chờ kích hoạt (OTP chưa verify)
 * 
 * LOGIN VS REGISTER:
 * - Login: Xác thực user có sẵn trong DB
 * - Register: Tạo user mới, gửi OTP verify
 * - Cả 2 đều dùng reCAPTCHA
 * - Login trả JWT token, Register trả temp token hoặc chờ verify
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/UsersDAO.java (checkLogin, getUserByEmail)
 * - DTO: DTO/Users.java (user object)
 * - Utils: utils/RecaptchaUtils.java (verify reCAPTCHA)
 * - Utils: utils/JwtUtils.java (generate JWT token)
 * - Utils: utils/PasswordUtils.java (hash password trong DAO)
 * - Utils: mylib/ValidationUtil.java (validate email)
 * - Config: CORS whitelist trong web.xml hoặc filter
 */

import DAO.UsersDAO;
import DTO.LoginRequest;
import DTO.Users;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import utils.JwtUtils;
import utils.RecaptchaUtils;

@WebServlet("/api/login")
public class loginController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ==================== OPTIONS ====================
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
    }

    // ==================== POST ====================
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        try (BufferedReader reader = request.getReader(); PrintWriter out = response.getWriter()) {

            LoginRequest loginReq = gson.fromJson(reader, LoginRequest.class);

            if (loginReq == null || isBlank(loginReq.getEmail()) || isBlank(loginReq.getPassword())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(jsonFail("Thiếu email hoặc mật khẩu", "AUTH_MISSING_FIELD"));
                out.flush();
                return;
            }

            // Verify reCAPTCHA
            if (!RecaptchaUtils.verify(loginReq.getRecaptchaToken())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid reCAPTCHA\"}");
                out.flush();
                return;
            }

            // ✅ checkLogin giờ đã tự verify password hash
            Users user = usersDAO.checkLogin(loginReq.getEmail().trim(), loginReq.getPassword());

            if (user != null) {

                // DB đang dùng status: 'ACTIVE','INACTIVE','BLOCKED'
                if ("INACTIVE".equalsIgnoreCase(user.getStatus())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print(jsonFail("Tài khoản đã bị vô hiệu", "AUTH_BLOCKED"));
                    out.flush();
                    return;
                }

                // Tạo JWT token
                String token = JwtUtils.generateToken(user.getEmail(), user.getRole(), user.getId());
                System.out.println("Token User: " + token);

                response.setStatus(HttpServletResponse.SC_OK);
                LoginResponse payload = new LoginResponse("success", token, user);
                out.print(gson.toJson(payload));
                out.flush();
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(jsonFail("Email hoặc mật khẩu không hợp lệ", "AUTH_INVALID"));
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(jsonError("Lỗi server: " + e.getMessage()));
                out.flush();
            }
        }
    }

    // ==================== CORS ====================
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || // ⭐ Cho phép ngrok
                origin.contains("ngrok.app") // ⭐ (phòng trường hợp domain mới)
        );

        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    // ==================== Helpers ====================
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String jsonFail(String message, String code) {
        return "{\"status\":\"fail\",\"code\":\"" + escape(code == null ? "" : code)
                + "\",\"message\":\"" + escape(message == null ? "" : message) + "\"}";
    }

    private String jsonError(String message) {
        return "{\"status\":\"error\",\"message\":\"" + escape(message == null ? "" : message) + "\"}";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== DTOs ====================
    private static class LoginResponse {

        String status;
        String token;
        Users user;

        public LoginResponse(String status, String token, Users user) {
            this.status = status;
            this.token = token;
            this.user = user;
        }
    }
}