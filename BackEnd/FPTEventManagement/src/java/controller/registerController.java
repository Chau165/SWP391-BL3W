package controller;

/**
 * ========================================================================================================
 * CONTROLLER: registerController - ĐĂNG KÝ TÀI KHOẢN MỚI
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Đăng ký tài khoản mới cho user (STUDENT role mặc định)
 * - Xác thực reCAPTCHA để chống bot
 * - Validate các trường dữ liệu (full name, email, phone, password)
 * - Kiểm tra email đã tồn tại chưa
 * - Hash password trước khi lưu database
 * - Tạo user mới trong bảng Users
 * - Tự động login sau khi đăng ký (trả JWT token)
 * 
 * ENDPOINT: POST /api/register
 * 
 * REQUEST BODY:
 * {
 *   "fullName": "Nguyễn Văn A",
 *   "email": "a@fpt.edu.vn",
 *   "phone": "0901234567",
 *   "password": "Pass123",
 *   "recaptchaToken": "03AGdBq27..."
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "success",
 *   "message": "Registered and logged in successfully",
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "user": {
 *     "id": 123,
 *     "fullName": "Nguyễn Văn A",
 *     "email": "a@fpt.edu.vn",
 *     "phone": "0901234567",
 *     "role": "STUDENT",
 *     "status": "ACTIVE"
 *   }
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Thiếu field, validation failed, reCAPTCHA invalid
 * - 409 Conflict: Email đã tồn tại
 * - 500 Internal Server Error: Lỗi database
 * 
 * LUỒNG XỬ LÝ:
 * 1. Parse JSON request body
 * 2. Verify reCAPTCHA token với Google API
 * 3. Validate full name (2-100 ký tự, chỉ chữ cái và khoảng trắng)
 * 4. Validate phone (10-11 số, bắt đầu 0)
 * 5. Validate email (format @fpt.edu.vn)
 * 6. Validate password (tối thiểu 6 ký tự, có chữ và số)
 * 7. Kiểm tra email đã tồn tại (UsersDAO.existsByEmail)
 * 8. Tạo đối tượng Users với role="STUDENT", status="ACTIVE"
 * 9. Hash password bằng SHA-256 (PasswordUtils.hashPassword)
 * 10. Insert vào database (UsersDAO.insertUser)
 * 11. Lấy thông tin user vừa tạo (UsersDAO.findById)
 * 12. Sinh JWT token (JwtUtils.generateToken)
 * 13. Trả về token + user info cho FE
 * 14. FE lưu token và chuyển về trang chủ
 * 
 * VALIDATION RULES:
 * - Full Name: 2-100 ký tự, chỉ chữ cái và khoảng trắng
 * - Email: Phải theo format xxx@fpt.edu.vn
 * - Phone: 10-11 số, bắt đầu bằng 0
 * - Password: Tối thiểu 6 ký tự, bao gồm cả chữ và số
 * 
 * DATABASE:
 * - Bảng Users: Lưu thông tin user mới
 * - Columns: user_id, full_name, email, phone, password_hash, role, status, created_at
 * - Default values: role="STUDENT", status="ACTIVE"
 * 
 * SECURITY:
 * - reCAPTCHA: Chống bot đăng ký spam
 * - Password hash: SHA-256 (nên nâng cấp lên BCrypt)
 * - Email validation: Chỉ cho phép @fpt.edu.vn
 * - JWT token: 7 ngày expiration
 * 
 * SO SÁNH VỚI OTP REGISTER:
 * - Phương pháp này: Tạo user ngay lập tức, không cần verify OTP
 * - Phương pháp OTP: Gửi OTP trước, verify rồi mới tạo user
 * - Phương pháp này đơn giản hơn nhưng ít bảo mật hơn
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/UsersDAO.java (insert user, check email exists)
 * - DTO: DTO/RegisterRequest.java, DTO/Users.java
 * - Utils: utils/RecaptchaUtils.java (verify reCAPTCHA)
 * - Utils: utils/PasswordUtils.java (hash password)
 * - Utils: utils/JwtUtils.java (generate token)
 * - Utils: mylib/ValidationUtil.java (validate input)
 */

import DAO.UsersDAO;
import DTO.RegisterRequest;
import DTO.Users;
import com.google.gson.Gson;
import mylib.ValidationUtil;
import utils.JwtUtils;
import utils.PasswordUtils;
import utils.RecaptchaUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register")
public class registerController extends HttpServlet {

    // Gson để parse JSON request body và convert object -> JSON
    private final Gson gson = new Gson();

    /**
     * ====================================================================================================
     * METHOD: isBlank - KIỂM TRA CHUỖI RỖNG
     * ====================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Helper method để kiểm tra String null hoặc empty
     * - Thay thế cho String.isBlank() (Java 11+) để tương thích Java 8
     * 
     * LOGIC:
     * - Trả true nếu s == null HOẶC s.trim() rỗng
     * - Dùng để validate các field không được để trống
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * ====================================================================================================
     * METHOD: doOptions - XỬ LÝ PREFLIGHT REQUEST (CORS)
     * ====================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Xử lý OPTIONS request từ browser khi FE gọi API từ domain khác
     * - Browser tự động gửi OPTIONS trước POST để kiểm tra CORS
     * 
     * FLOW:
     * 1. Browser phát hiện cross-origin POST request
     * 2. Browser gửi OPTIONS request (preflight)
     * 3. Server trả về 204 No Content + CORS headers
     * 4. Browser cho phép POST request thực sự
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req); // Thiết lập CORS headers
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204 No Content
    }

    /**
     * ====================================================================================================
     * METHOD: doPost - XỬ LÝ ĐĂNG KÝ TÀI KHOẢN MỚI
     * ====================================================================================================
     * 
     * ENDPOINT: POST /api/register
     * CONTENT-TYPE: application/json
     * 
     * REQUEST FLOW:
     * 1. Parse JSON body thành RegisterRequest object
     * 2. Verify reCAPTCHA token với Google API
     * 3. Validate tất cả các field (full name, email, phone, password)
     * 4. Kiểm tra email đã tồn tại chưa
     * 5. Tạo Users object với role="STUDENT", status="ACTIVE"
     * 6. Hash password bằng SHA-256
     * 7. Insert user vào database
     * 8. Generate JWT token
     * 9. Trả về token + user info
     * 
     * DATABASE OPERATIONS:
     * - UsersDAO.existsByEmail(email): Kiểm tra email trùng
     *   Query: SELECT COUNT(*) FROM Users WHERE email = ?
     * 
     * - UsersDAO.insertUser(u): Tạo user mới
     *   Query: INSERT INTO Users (full_name, email, phone, password_hash, role, status, created_at)
     *          VALUES (?, ?, ?, ?, ?, ?, GETDATE())
     *   Return: user_id của user vừa tạo
     * 
     * - UsersDAO.findById(id): Lấy thông tin user sau khi tạo
     *   Query: SELECT * FROM Users WHERE user_id = ?
     * 
     * ERROR HANDLING:
     * - 400: Invalid input, validation failed, reCAPTCHA failed
     * - 409: Email đã tồn tại
     * - 500: Database error
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Thiết lập encoding UTF-8 để xử lý tiếng Việt đúng
        // Request encoding: Parse JSON body với ký tự tiếng Việt
        req.setCharacterEncoding("UTF-8");
        
        // Response encoding: Trả JSON có tiếng Việt về FE
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");
        
        // Thiết lập CORS headers để FE có thể đọc response
        setCorsHeaders(resp, req);

        // Try-with-resources: Tự động đóng reader và writer sau khi xong
        try (BufferedReader reader = req.getReader(); PrintWriter out = resp.getWriter()) {

            // ===== 1. PARSE JSON REQUEST BODY =====
            // Gson tự động convert JSON -> RegisterRequest object
            // JSON: {"fullName": "...", "email": "...", ...}
            RegisterRequest input = gson.fromJson(reader, RegisterRequest.class);

            // Kiểm tra request body có hợp lệ không (null = invalid JSON)
            if (input == null) {
                resp.setStatus(400); // 400 Bad Request
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            // ===== 2. VERIFY reCAPTCHA =====
            // Gọi Google reCAPTCHA API để verify token từ FE
            // RecaptchaUtils gửi POST request tới https://www.google.com/recaptcha/api/siteverify
            // Google trả về {success: true/false}
            if (!RecaptchaUtils.verify(input.getRecaptchaToken())) {
                resp.setStatus(400);
                out.print("{\"error\": \"Invalid reCAPTCHA\"}");
                return;
            }

            // ===== 3. VALIDATE CÁC FIELD =====
            
            // Validate Full Name: 2-100 ký tự, chỉ chữ cái và khoảng trắng
            // ValidationUtil.isValidFullName() check regex: ^[a-zA-ZÀ-ỹ\\s]{2,100}$
            if (!ValidationUtil.isValidFullName(input.getFullName())) {
                resp.setStatus(400);
                out.print("{\"error\":\"Full name is invalid\"}");
                return;
            }
            
            // Validate Phone: 10-11 số, bắt đầu bằng 0
            // ValidationUtil.isValidVNPhone() check regex: ^0\\d{9,10}$
            if (!ValidationUtil.isValidVNPhone(input.getPhone())) {
                resp.setStatus(400);
                out.print("{\"error\":\"Phone number is invalid\"}");
                return;
            }
            
            // Validate Email: Phải theo format @fpt.edu.vn
            // ValidationUtil.isValidEmail() check regex: ^[a-zA-Z0-9._%+-]+@fpt\\.edu\\.vn$
            if (!ValidationUtil.isValidEmail(input.getEmail())) {
                resp.setStatus(400);
                out.print("{\"error\":\"Email is invalid\"}");
                return;
            }
            
            // Validate Password: Tối thiểu 6 ký tự, có cả chữ và số
            // ValidationUtil.isValidPassword() check length >= 6 và chứa cả letters + digits
            if (!ValidationUtil.isValidPassword(input.getPassword())) {
                resp.setStatus(400);
                out.print("{\"error\":\"Password must be at least 6 characters, include letters and digits\"}");
                return;
            }

            // ===== 4. KHỞI TẠO DAO =====
            // UsersDAO kết nối tới database qua DBUtils.getConnection()
            // DBUtils trả về Connection tới SQL Server
            UsersDAO dao = new UsersDAO();

            // ===== 5. KIỂM TRA EMAIL ĐÃ TỒN TẠI =====
            // Query: SELECT COUNT(*) FROM Users WHERE email = ?
            // Trả true nếu email đã có trong DB
            if (dao.existsByEmail(input.getEmail())) {
                resp.setStatus(409); // 409 Conflict
                out.print("{\"error\":\"Email already exists\"}");
                return;
            }

            // ===== 6. TẠO ĐỐI TƯỢNG USERS =====
            // Users là DTO tương ứng với bảng Users trong SQL Server
            Users u = new Users();
            u.setFullName(input.getFullName());
            u.setPhone(input.getPhone());
            u.setEmail(input.getEmail());

            // Hash password bằng SHA-256 trước khi lưu DB
            // PasswordUtils.hashPassword() dùng MessageDigest SHA-256
            // Output: 64 ký tự hex string
            String hash = PasswordUtils.hashPassword(input.getPassword());
            u.setPasswordHash(hash);

            // Set role và status mặc định
            // STUDENT: User thường, tham gia event
            // ACTIVE: Tài khoản hoạt động, có thể login
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");

            // ===== 7. INSERT VÀO DATABASE =====
            // Query: INSERT INTO Users (full_name, email, phone, password_hash, role, status, created_at)
            //        VALUES (?, ?, ?, ?, ?, ?, GETDATE())
            // Return: user_id (auto-increment primary key)
            int newId = dao.insertUser(u);
            
            // Kiểm tra insert có thành công không
            // newId <= 0 = insert failed (database error hoặc constraint violation)
            if (newId <= 0) {
                resp.setStatus(400);
                out.print("{\"error\":\"Failed to create user\"}");
                return;
            }

            // ===== 8. LẤY THÔNG TIN USER VỪA TẠO =====
            // Query: SELECT user_id, full_name, email, phone, role, status, avatar, created_at
            //        FROM Users WHERE user_id = ?
            // Lấy lại để có đầy đủ thông tin (bao gồm các field mặc định từ DB)
            Users newUser = dao.findById(newId);
            
            // Double-check: user phải tồn tại sau khi insert
            if (newUser == null) {
                resp.setStatus(500); // 500 Internal Server Error
                out.print("{\"error\":\"User created but cannot load profile\"}");
                return;
            }

            // ===== 9. SINH JWT TOKEN =====
            // JwtUtils.generateToken() tạo JWT với:
            // - Claims: email, role, userId
            // - Algorithm: HS256 (HMAC SHA-256)
            // - Expiration: 7 ngày (604800 seconds)
            // - Secret: Lấy từ JwtConfig.SECRET_KEY
            // Output: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
            String token = JwtUtils.generateToken(newUser.getEmail(), newUser.getRole(), newUser.getId());

            // ===== 10. TRẢ VỀ RESPONSE SUCCESS =====
            resp.setStatus(200); // 200 OK
            
            // Build JSON response manually (có thể dùng object + gson.toJson() cho clean hơn)
            out.print("{"
                    + "\"status\":\"success\"," 
                    + "\"message\":\"Registered and logged in successfully\"," 
                    + "\"token\":\"" + token + "\"," 
                    + "\"user\":" + gson.toJson(newUser) // Convert Users object -> JSON
                    + "}");
            
            // FE nhận được response, lưu token vào localStorage/sessionStorage
            // FE redirect về trang chủ, user đã login tự động
        }
    }

    /**
     * ====================================================================================================
     * METHOD: setCorsHeaders - THIẾT LẬP CORS HEADERS
     * ====================================================================================================
     * 
     * MỤC ĐÍCH:
     * - Cho phép FE (React/Vue/Angular) chạy trên domain khác gọi API
     * - Whitelist các domain được phép: localhost:3000, localhost:5173, ngrok
     * 
     * CORS (Cross-Origin Resource Sharing):
     * - Browser mặc định block request giữa các domain khác nhau (security)
     * - VD: FE chạy trên http://localhost:3000, API chạy trên http://localhost:8080
     * - Cần set headers để browser cho phép
     * 
     * CORS HEADERS:
     * - Access-Control-Allow-Origin: Domain được phép gọi API
     * - Access-Control-Allow-Credentials: Cho phép gửi cookies/credentials
     * - Access-Control-Allow-Methods: Các HTTP methods được phép
     * - Access-Control-Allow-Headers: Các headers FE được phép gửi
     * - Access-Control-Expose-Headers: Các headers FE được phép đọc từ response
     * - Access-Control-Max-Age: Thời gian cache preflight request
     * 
     * WHITELISTED ORIGINS:
     * - http://localhost:5173 (Vite dev server - React/Vue)
     * - http://localhost:3000 (Create React App, Next.js)
     * - *.ngrok-free.app / *.ngrok.app (Ngrok tunneling cho test mobile)
     * 
     * FLOW:
     * 1. Lấy Origin header từ request (domain mà FE đang chạy)
     * 2. Kiểm tra Origin có trong whitelist không
     * 3. Nếu có: Set Access-Control-Allow-Origin = Origin đó
     * 4. Nếu không: Set = "null" (block request)
     * 5. Set các headers khác để config CORS chi tiết
     */
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        // Lấy Origin header từ request
        // Origin = domain mà FE đang chạy (ví dụ: http://localhost:3000)
        String origin = req.getHeader("Origin");

        // Kiểm tra origin có trong danh sách whitelist không
        // Cho phép:
        // - localhost:5173 (Vite dev server)
        // - localhost:3000 (Create React App, Next.js)
        // - ngrok domains (cho test trên mobile/public internet)
        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app") // ⭐ Cho phép ngrok
                || origin.contains("ngrok.app") // ⭐ Phòng trường hợp domain mới
        );

        if (allowed) {
            // CHO PHÉP origin này gọi API
            // Set Access-Control-Allow-Origin = origin cụ thể (không dùng "*" vì có credentials)
            res.setHeader("Access-Control-Allow-Origin", origin);
            
            // Cho phép gửi credentials (cookies, Authorization header)
            // Cần thiết khi FE gửi JWT token trong header
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // BLOCK origin không trong whitelist
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Header Vary: Origin
        // Báo cho browser/proxy biết response phụ thuộc vào Origin
        // Giúp browser cache đúng cho từng origin khác nhau
        res.setHeader("Vary", "Origin");
        
        // Cho phép các HTTP methods: GET, POST, OPTIONS
        // OPTIONS: Dùng cho preflight request
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        
        // Cho phép FE gửi các headers này trong request:
        // - Content-Type: application/json (gửi JSON body)
        // - Authorization: Bearer <token> (gửi JWT token)
        // - ngrok-skip-browser-warning: Skip ngrok warning page (khi dùng ngrok)
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        
        // Cho phép FE đọc Authorization header từ response
        // Dùng khi backend trả về JWT token mới trong response header
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        
        // Cache preflight request trong 24 giờ (86400 giây)
        // Browser không cần gửi OPTIONS request lại trong 24h
        // Giúp giảm số lượng request, tăng performance
        res.setHeader("Access-Control-Max-Age", "86400");
    }

}
