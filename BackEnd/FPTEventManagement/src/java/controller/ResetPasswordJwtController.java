package controller;

import DAO.UsersDAO;
import com.google.gson.Gson;
import utils.ResetJwtUtil;
import utils.PasswordResetManager; // ✅ dùng OTP

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

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        PrintWriter out = response.getWriter();

        // Đọc body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line; while ((line = reader.readLine()) != null) sb.append(line);
        }

        Req body = gson.fromJson(sb.toString(), Req.class);
        if (body == null || isBlank(body.token) || isBlank(body.otp) || isBlank(body.newPassword)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token/OTP/mật khẩu mới\"}");
            return;
        }

        if (body.newPassword.length() < 6) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Mật khẩu phải có ít nhất 6 ký tự\"}");
            return;
        }

        // 1) Decode JWT
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

        // 3) Đổi mật khẩu
        boolean ok;
        try {
            ok = usersDAO.updatePasswordByEmail(email, body.newPassword);
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        if (!ok) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật mật khẩu\"}");
            return;
        }

        // 4) Vơ hiệu OTP sau khi dùng
        PasswordResetManager.invalidate(email);

        response.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đổi mật khẩu thành công\"}");
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

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

    private static class Req {
        String token;
        String otp;          // ✅ thêm OTP
        String newPassword;
    }
}
