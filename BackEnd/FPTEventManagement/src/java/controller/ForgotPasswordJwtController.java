package controller;

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.EmailService;
import utils.ResetJwtUtil;
import utils.PasswordResetManager; // ✅ thêm

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/forgot-password
 * Body: { email }
 * Tạo JWT + OTP và gửi về email
 */
@WebServlet("/api/forgot-password")
public class ForgotPasswordJwtController extends HttpServlet {
    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        // Đọc JSON body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line; while ((line = reader.readLine()) != null) sb.append(line);
        }

        Req body = gson.fromJson(sb.toString(), Req.class);
        PrintWriter out = response.getWriter();

        if (body == null || body.email == null || body.email.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không được để trống\"}");
            return;
        }

        String email = body.email.trim();
        if (!mylib.ValidationUtil.isValidEmail(email)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Email không hợp lệ\"}");
            return;
        }

        Users user = usersDAO.getUserByEmail(email);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Email không tồn tại trong hệ thống\"}");
            return;
        }

        // ✅ Tạo token JWT (10 phút)
        String token = ResetJwtUtil.generateResetToken(user.getId(), email);

        // ✅ Sinh OTP (5 phút, 1 lần dùng)
        String otp = PasswordResetManager.generateOtp(email);

        // Link FE
      String resetLink = "http://localhost:5173/#/reset-pass?token=" + token;

        // ✅ Gửi email: cả link + OTP
        String html = "<h2>🔐 Đặt lại mật khẩu</h2>"
                + "<p>Xin chào, <b>" + user.getFullName() + "</b></p>"
                + "<p>Mã OTP của bạn (hết hạn 5 phút): <b style='font-size:18px;letter-spacing:2px;'>" + otp + "</b></p>"
                + "<p>Nhấn vào liên kết sau để mở trang đặt lại mật khẩu (hiệu lực 10 phút):</p>"
                + "<p><a href='" + resetLink + "' "
                + "style='background:#2563eb;color:white;padding:10px 15px;border-radius:6px;text-decoration:none;'>Đặt lại mật khẩu</a></p>"
                + "<p>Nếu bạn không yêu cầu, vui lòng bỏ qua email này.</p>";

        boolean sent = EmailService.sendCustomEmail(email, "Đặt lại mật khẩu - EV Battery Swap", html);

        if (!sent) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể gửi email\"}");
            return;
        }

        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đã gửi OTP và link đặt lại mật khẩu tới email\"}");
    }

    // CORS
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        boolean allowed = origin != null && (
                origin.equals("http://localhost:5173") ||
                origin.equals("http://127.0.0.1:5173")
        );
        res.setHeader("Access-Control-Allow-Origin", allowed ? origin : "null");
        res.setHeader("Access-Control-Allow-Credentials", "true");
        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    private static class Req { String email; }
}
