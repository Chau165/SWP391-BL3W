package controller;

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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");

        PrintWriter out = response.getWriter();

        // ===== 1. Đọc JSON body =====
        StringBuilder sb = new StringBuilder();
        try ( BufferedReader reader = request.getReader()) {
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
                html
        );

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
