package controller;

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import utils.PasswordResetManager; // dùng OTP

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

/**
 * POST /api/reset-password
 * Body: { "email": "xxx@fpt.edu.vn", "otp": "123456", "newPassword": "Abc@123" }
 *
 * Flow:
 *  1. Kiểm tra email, otp, newPassword không rỗng
 *  2. Kiểm tra mật khẩu đủ mạnh (tối thiểu 6 ký tự – bạn có thể tăng thêm rule)
 *  3. Kiểm tra email có tồn tại trong hệ thống
 *  4. Verify OTP bằng PasswordResetManager (theo email)
 *  5. Nếu OK -> cập nhật mật khẩu mới cho user (hash trong DAO)
 *  6. Vô hiệu hóa OTP sau khi dùng
 */
@WebServlet("/api/reset-password")
public class ResetPasswordJwtController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ===== DTO request =====
    private static class Req {
        String email;
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

        // ===== 1. Đọc body JSON =====
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        Req body = gson.fromJson(sb.toString(), Req.class);
        if (body == null || isBlank(body.email) || isBlank(body.otp) || isBlank(body.newPassword)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu email/OTP/mật khẩu mới\"}");
            return;
        }

        String email = body.email.trim();
        String otp = body.otp.trim();
        String newPassword = body.newPassword;

        // ===== 2. Validate đơn giản mật khẩu =====
        if (newPassword.length() < 6) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Mật khẩu phải có ít nhất 6 ký tự\"}");
            return;
        }

        // ===== 3. Kiểm tra email tồn tại =====
        Users user = usersDAO.getUserByEmail(email);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Email không tồn tại trong hệ thống\"}");
            return;
        }

        // ===== 4. Verify OTP theo email =====
        boolean otpOk = PasswordResetManager.verifyOtp(email, otp);
        if (!otpOk) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"OTP không đúng hoặc đã hết hạn\"}");
            return;
        }

        // ===== 5. Đổi mật khẩu (hash bên trong DAO) =====
        boolean ok;
        try {
            ok = usersDAO.updatePasswordByEmail(email, newPassword);
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

        // ===== 6. Vô hiệu OTP sau khi dùng =====
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

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

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
