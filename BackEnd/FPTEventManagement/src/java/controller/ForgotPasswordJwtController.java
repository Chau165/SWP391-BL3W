package controller;

/**
 * ========================================================================================================
 * CONTROLLER: ForgotPasswordJwtController - QUÊN MẬT KHẨU - GỬI OTP QUA EMAIL
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Nhận email từ user quên mật khẩu
 * - Validate email format và kiểm tra email có tồn tại trong hệ thống
 * - Sinh mã OTP 6 chữ số ngẫu nhiên
 * - Lưu OTP vào PasswordResetManager (in-memory cache, TTL 5 phút)
 * - Gửi OTP qua email cho user
 * - Trả về response cho Frontend
 * 
 * ENDPOINT: POST /api/forgot-password
 * 
 * REQUEST BODY:
 * {
 *   "email": "a@fpt.edu.vn"
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "success",
 *   "message": "Đã gửi OTP đặt lại mật khẩu tới email"
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Email rỗng, email không hợp lệ
 * - 404 Not Found: Email không tồn tại trong hệ thống
 * - 500 Internal Server Error: Không thể gửi email
 * 
 * LUỒNG XỬ LÝ:
 * 1. FE gửi POST request với email
 * 2. Parse JSON request body
 * 3. Validate email format (ValidationUtil.isValidEmail)
 * 4. Tìm user theo email (UsersDAO.getUserByEmail)
 * 5. Nếu không tìm thấy -> 404 Not Found
 * 6. Sinh OTP 6 chữ số (PasswordResetManager.generateOtp)
 * 7. Lưu OTP vào cache với TTL 5 phút
 * 8. Soạn email HTML chứa OTP
 * 9. Gửi email (EmailService.sendCustomEmail)
 * 10. Trả về success response
 * 11. FE chuyển user sang màn hình nhập OTP
 * 
 * EMAIL CONTENT:
 * - HTML format, hiển thị OTP to, rõ ràng
 * - Thông báo OTP có hiệu lực 5 phút
 * - Hướng dẫn user nhập OTP vào hệ thống
 * - Lưu ý: Nếu không yêu cầu, bỏ qua email
 * 
 * SECURITY:
 * - OTP chỉ có hiệu lực 5 phút (TTL)
 * - Tối đa 5 lần nhập sai (MAX_ATTEMPTS)
 * - OTP chỉ dùng được 1 lần (one-time use)
 * - Không gửi link reset password (tránh token hijacking)
 * - Nên thêm rate limiting để tránh spam
 * 
 * SO VỚI JWT RESET PASSWORD:
 * - Phương pháp cũ: Gửi link chứa JWT token trong email
 * - Phương pháp mới (hiện tại): Gửi OTP, user nhập OTP trong app
 * - Lợi ích: An toàn hơn (không có link public), UX tốt hơn (không phải mở email)
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/UsersDAO.java (kiểm tra email tồn tại)
 * - Utils: mylib/ValidationUtil.java (validate email format)
 * - Service: mylib/EmailService.java (gửi email)
 * - Manager: utils/PasswordResetManager.java (quản lý OTP)
 * - Next step: controller/ResetPasswordJwtController.java (verify OTP và đổi mật khẩu)
 */

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.EmailService;
import mylib.ValidationUtil;
import utils.PasswordResetManager; // ❗ vẫn dùng để quản lý OTP

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/forgot-password Body: { "email": "xxx@fpt.edu.vn" }
 *
 * ✅ Chức năng: - Kiểm tra email - Sinh OTP (lưu tạm trong PasswordResetManager,
 * ví dụ hết hạn 5 phút) - Gửi OTP qua email cho user
 *
 * ❌ Không sinh JWT token, không gửi link reset password.
 */
@WebServlet("/api/forgot-password")
public class ForgotPasswordJwtController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ====== DTO nhận request ======
    private static class Req {

        String email;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * XỬ LÝ REQUEST QUÊN MẬT KHẨU - GỬI OTP
     * 
     * ENDPOINT: POST /api/forgot-password
     * AUTHENTICATION: Không cần JWT (public endpoint)
     * CONTENT-TYPE: application/json
     * 
     * REQUEST FLOW:
     * 1. Parse email từ request body
     * 2. Validate email format và kiểm tra tồn tại trong DB
     * 3. Sinh OTP 6 chữ số và lưu vào PasswordResetManager
     * 4. Gửi OTP qua email
     * 5. Return success response
     * 
     * ERROR HANDLING:
     * - 400: Email rỗng hoặc không hợp lệ
     * - 404: Email không tồn tại trong hệ thống
     * - 500: Lỗi server (gửi email, database...)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        PrintWriter out = response.getWriter();

        // ===== 1. Đọc JSON body =====
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        Req body = gson.fromJson(sb.toString(), Req.class);

        if (body == null || body.email == null || body.email.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không được để trống\"}");
            return;
        }

        String email = body.email.trim();

        // ===== 2. Validate email format =====
        if (!ValidationUtil.isValidEmail(email)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không hợp lệ\"}");
            return;
        }

        // ===== 3. Tìm user theo email =====
        Users user = usersDAO.getUserByEmail(email);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Email không tồn tại trong hệ thống\"}");
            return;
        }

        // ===== 4. Sinh OTP (không sinh token nữa) =====
        // PasswordResetManager sẽ chịu trách nhiệm lưu OTP + thời gian hết hạn
        String otp = PasswordResetManager.generateOtp(email);

        // ===== 5. Soạn nội dung email CHỈ chứa OTP =====
        String html = "<h2>🔐 Đặt lại mật khẩu - FPT Event Management</h2>"
                + "<p>Xin chào, <b>" + escapeHtml(user.getFullName()) + "</b></p>"
                + "<p>Mã OTP đặt lại mật khẩu của bạn (hiệu lực trong 5 phút):</p>"
                + "<p style='font-size:20px;letter-spacing:3px;'><b>" + otp + "</b></p>"
                + "<p>Vui lòng nhập mã OTP này vào màn hình đặt lại mật khẩu trên hệ thống.</p>"
                + "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>"
                + "<hr><p style='font-size:12px;color:#666;'>FPT Event Management System</p>";

        boolean sent = EmailService.sendCustomEmail(
                email,
                "Mã OTP đặt lại mật khẩu - FPT Event Management",
                html);

        if (!sent) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể gửi email đặt lại mật khẩu\"}");
            return;
        }

        // ===== 6. Trả kết quả =====
        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đã gửi OTP đặt lại mật khẩu tới email\"}");
    }

    // ====== CORS giống các controller khác ======
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

    // Helper escape đơn giản cho fullName khi đưa vào HTML
    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}