package controller;

import DAO.UsersDAO;
import com.google.gson.Gson;
import utils.ResetJwtUtil;
import utils.PasswordResetManager; // dùng OTP

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/reset-password
 * Body: { token, otp, newPassword }
 */
@WebServlet("/api/reset-password")
public class ResetPasswordJwtController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ===== DTO request =====
    private static class Req {
        String token;
        String otp;
        String newPassword;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");
        request.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // Đọc body JSON
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        Req body = gson.fromJson(sb.toString(), Req.class);
        if (body == null || isBlank(body.token) || isBlank(body.otp) || isBlank(body.newPassword)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token/OTP/mật khẩu mới\"}");
            return;
        }

        // Validate đơn giản mật khẩu
        if (body.newPassword.length() < 6) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Mật khẩu phải có ít nhất 6 ký tự\"}");
            return;
        }

        // 1) Decode JWT reset
        String email = ResetJwtUtil.getEmail(body.token);
        Integer uid  = ResetJwtUtil.getUserId(body.token);
        if (email == null || uid == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ hoặc đã hết hạn\"}");
            return;
        }

        // 2) Verify OTP theo email
        boolean otpOk = PasswordResetManager.verifyOtp(email, body.otp.trim());
        if (!otpOk) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"OTP không đúng hoặc đã hết hạn\"}");
            return;
        }

        // 3) Đổi mật khẩu (hash bên trong DAO)
        boolean ok;
        try {
            ok = usersDAO.updatePasswordByEmail(email, body.newPassword);
        } catch (Exception ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        if (!ok) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        // 4) Vô hiệu OTP sau khi dùng
        PasswordResetManager.invalidate(email);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đổi mật khẩu thành công\"}");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ==== CORS giống các controller khác ====
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
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
