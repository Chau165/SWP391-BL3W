package controller;

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.EmailService;
import mylib.ValidationUtil;
import utils.ResetJwtUtil;
import utils.PasswordResetManager;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/forgot-password
 * Body: { "email": "xxx@fpt.edu.vn" }
 * -> Kiểm tra email, sinh JWT reset + OTP, gửi mail cho user
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

        // Đọc JSON body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        Req body = gson.fromJson(sb.toString(), Req.class);

        if (body == null || body.email == null || body.email.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không được để trống\"}");
            return;
        }

        String email = body.email.trim();

        // Validate email format
        if (!ValidationUtil.isValidEmail(email)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không hợp lệ\"}");
            return;
        }

        // Tìm user theo email trong DB FPTEventManagement.dbo.Users
        Users user = usersDAO.getUserByEmail(email);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Email không tồn tại trong hệ thống\"}");
            return;
        }

        // ✅ Tạo token reset (JWT, ví dụ hết hạn 10 phút – tuỳ bạn cấu hình trong ResetJwtUtil)
        String token = ResetJwtUtil.generateResetToken(user.getId(), email);

        // ✅ Sinh OTP (hết hạn 5 phút, 1 lần dùng) lưu trong PasswordResetManager
        String otp = PasswordResetManager.generateOtp(email);

        // Link FE để redirect tới trang nhập OTP + mật khẩu mới
        // Tuỳ FE của bạn dùng router gì, chỉnh lại path cho đúng
        String resetLink = "http://localhost:5173/reset-password?token=" + token;

        // ✅ Nội dung email cho hệ thống FPT Event Management
        String html = "<h2>🔐 Đặt lại mật khẩu - FPT Event Management</h2>"
                + "<p>Xin chào, <b>" + escapeHtml(user.getFullName()) + "</b></p>"
                + "<p>Mã OTP của bạn (hiệu lực 5 phút):</p>"
                + "<p style='font-size:18px;letter-spacing:3px;'><b>" + otp + "</b></p>"
                + "<p>Nhấn vào liên kết sau để mở trang đặt lại mật khẩu (token hiệu lực trong một thời gian ngắn):</p>"
                + "<p><a href='" + resetLink + "' "
                + "style='background:#2563eb;color:#fff;padding:10px 16px;"
                + "border-radius:6px;text-decoration:none;'>Đặt lại mật khẩu</a></p>"
                + "<p>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.</p>"
                + "<hr><p style='font-size:12px;color:#666;'>FPT Event Management System</p>";

        boolean sent = EmailService.sendCustomEmail(email, "Đặt lại mật khẩu - FPT Event Management", html);

        if (!sent) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể gửi email đặt lại mật khẩu\"}");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đã gửi OTP và link đặt lại mật khẩu tới email\"}");
    }

    // ====== CORS giống các controller khác ======
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        boolean allowed = origin != null && (
                "http://localhost:5173".equals(origin) ||
                "http://127.0.0.1:5173".equals(origin) ||
                origin.endsWith(".ngrok-free.app") ||
                origin.endsWith(".ngrok.app")
        );

        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    // Helper escape đơn giản cho fullName khi đưa vào HTML
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
