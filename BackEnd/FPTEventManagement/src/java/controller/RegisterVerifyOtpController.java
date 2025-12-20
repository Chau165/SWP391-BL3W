package controller;

/**
 * ========================================================================================================
 * CONTROLLER: RegisterVerifyOtpController - XÁC THỰC OTP VÀ TẠO TÀI KHOẢN
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Nhận email và OTP từ Frontend
 * - Verify OTP từ OtpCache
 * - Kiểm tra OTP hết hạn, số lần nhập sai
 * - Hash password và tạo tài khoản mới trong database
 * - Sinh JWT token cho user mới
 * - Trả về token + thông tin user cho FE
 * 
 * ENDPOINT: POST /api/register/verify-otp
 * 
 * REQUEST BODY:
 * {
 *   "email": "a@fpt.edu.vn",
 *   "otp": "123456"
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "success",
 *   "message": "Registered and logged in successfully",
 *   "token": "eyJhbGc...",
 *   "user": {
 *     "id": 123,
 *     "fullName": "Nguyễn Văn A",
 *     "email": "a@fpt.edu.vn",
 *     "phone": "0912345678",
 *     "role": "STUDENT",
 *     "status": "ACTIVE"
 *   }
 * }
 * 
 * RESPONSE ERROR:
 * - 400: OTP not found / OTP expired / OTP incorrect
 * - 409: Email already exists (race condition)
 * - 429: Too many attempts
 * - 400: Failed to create user
 * - 500: User created but cannot load profile
 * 
 * LUỒNG XỬ LÝ:
 * 1. Parse request: email + otp
 * 2. Lấy PendingUser từ OtpCache
 * 3. Check OTP expired (5 phút)
 * 4. Check attempts < 5 lần
 * 5. Verify OTP khớp
 * 6. Check email chưa tồn tại (double-check)
 * 7. Hash password (SHA-256)
 * 8. Insert vào bảng Users
 * 9. Lấy thông tin user vừa tạo
 * 10. Xóa OTP khỏi cache
 * 11. Sinh JWT token
 * 12. Trả về token + user info
 * 
 * SECURITY:
 * - Password được hash SHA-256 trước khi lưu DB
 * - OTP chỉ có hiệu lực 5 phút
 * - Tối đa 5 lần nhập sai OTP
 * - Sau 5 lần sai: phải request OTP mới
 * - JWT token có expiration time
 * 
 * KẾT NỐI FILE:
 * - Cache: mylib/OtpCache.java (verify OTP)
 * - DAO: DAO/UsersDAO.java (create user)
 * - Utils: utils/JwtUtils.java (generate token)
 * - Previous step: controller/RegisterSendOtpController.java
 * - Next step: FE lưu token và chuyển về trang chủ
 */

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.OtpCache;
import utils.JwtUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register/verify-otp")
public class RegisterVerifyOtpController extends HttpServlet {

    private final Gson gson = new Gson();

    static class VerifyRequest {

        String email;
        String otp;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        try (BufferedReader reader = req.getReader(); PrintWriter out = resp.getWriter()) {
            VerifyRequest input = gson.fromJson(reader, VerifyRequest.class);

            if (input == null || input.email == null || input.otp == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            OtpCache.PendingUser p = OtpCache.get(input.email);
            if (p == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"OTP not found. Please request a new one.\"}");
                return;
            }

            if (OtpCache.isExpired(p)) {
                OtpCache.remove(input.email);
                resp.setStatus(400);
                out.print("{\"error\":\"OTP expired. Please request a new one.\"}");
                return;
            }

            if (!OtpCache.canAttempt(p)) {
                OtpCache.remove(input.email);
                resp.setStatus(429);
                out.print("{\"error\":\"Too many attempts. Please request a new OTP.\"}");
                return;
            }

            if (!p.otp.equals(input.otp)) {
                OtpCache.incAttempt(p);
                resp.setStatus(400);
                out.print("{\"error\":\"OTP is incorrect\"}");
                return;
            }

            UsersDAO dao = new UsersDAO();
            if (dao.existsByEmail(p.email)) {
                OtpCache.remove(input.email);
                resp.setStatus(409);
                out.print("{\"error\":\"Email already exists\"}");
                return;
            }

            // Tạo Users từ PendingUser (đã hash password, set role/status)
            Users newUserEntity = p.toUsersEntity();
            int newId = dao.insertUser(newUserEntity);
            if (newId <= 0) {
                resp.setStatus(400);
                out.print("{\"error\":\"Failed to create user\"}");
                return;
            }

            Users newUser = dao.findById(newId);
            if (newUser == null) {
                resp.setStatus(500);
                out.print("{\"error\":\"User created but cannot load profile\"}");
                return;
            }

            // Xoá OTP khỏi cache
            OtpCache.remove(input.email);

            String token = JwtUtils.generateToken(newUser.getEmail(), newUser.getRole(), newUser.getId());

            resp.setStatus(200);
            out.print("{"
                    + "\"status\":\"success\","
                    + "\"message\":\"Registered and logged in successfully\","
                    + "\"token\":\"" + token + "\","
                    + "\"user\":" + gson.toJson(newUser)
                    + "}");
        }
    }

    // ========== CORS ==========
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

}