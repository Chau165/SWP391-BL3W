package controller;

/**
 * ========================================================================================================
 * CONTROLLER: RegisterResendOtpController - GỬI LẠI MÃ OTP
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Gửi lại OTP mới khi user chưa nhận được email hoặc OTP hết hạn
 * - Kiểm tra cooldown (60 giây giữa các lần gửi)
 * - Giới hạn số lần gửi lại (tối đa 5 lần)
 * - Sinh OTP mới và cập nhật cache
 * - Gửi OTP mới qua email
 * 
 * ENDPOINT: POST /api/register/resend-otp
 * 
 * REQUEST BODY:
 * {
 *   "email": "a@fpt.edu.vn"
 * }
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "status": "otp_resent",
 *   "message": "OTP has been resent to your email"
 * }
 * 
 * RESPONSE ERROR:
 * - 400: No pending registration (chưa gọi send-otp)
 * - 429: Resend blocked (chưa đủ 60s hoặc quá 5 lần)
 * - 502: Failed to send email
 * 
 * BUSINESS RULES:
 * - Cooldown: 60 giây giữa các lần gửi (tránh spam)
 * - Max resend: 5 lần (sau đó phải đăng ký lại từ đầu)
 * - OTP mới có TTL 5 phút
 * - Giữ nguyên thông tin đăng ký (fullName, phone, password)
 * - Reset attempt counter khi gửi OTP mới
 * 
 * LUỒNG XỬ LÝ:
 * 1. Parse request: email
 * 2. Lấy PendingUser từ OtpCache
 * 3. Check cache tồn tại (có request send-otp trước đó)
 * 4. Check cooldown (now - lastSentAt >= 60s)
 * 5. Check resend count < 5
 * 6. Sinh OTP mới
 * 7. Gửi email OTP mới
 * 8. Update cache: otp, expiresAt, lastSentAt, resendCount++
 * 9. Trả về success response
 * 
 * ANTI-SPAM:
 * - Cooldown 60s: Không cho gửi liên tục
 * - Max 5 lần: Tránh abuse email service
 * - Nên thêm rate limiting theo IP
 * 
 * KẾT NỐI FILE:
 * - Cache: mylib/OtpCache.java (update OTP)
 * - Service: mylib/EmailService.java (send email)
 * - Related: controller/RegisterSendOtpController.java, RegisterVerifyOtpController.java
 */

import com.google.gson.Gson;
import mylib.EmailService;
import mylib.OtpCache;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register/resend-otp")
public class RegisterResendOtpController extends HttpServlet {

    private final Gson gson = new Gson();

    static class ResendRequest {

        String email;
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
            ResendRequest input = gson.fromJson(reader, ResendRequest.class);

            if (input == null || input.email == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            OtpCache.PendingUser p = OtpCache.get(input.email);
            if (p == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"No pending registration for this email. Please submit information again.\"}");
                return;
            }

            if (!OtpCache.canResend(p)) {
                resp.setStatus(429);
                out.print("{\"error\":\"Resend is temporarily blocked. Please wait before requesting another OTP.\"}");
                return;
            }

            String newOtp = EmailService.generateOtp();
            boolean sent = EmailService.sendRegistrationOtpEmail(input.email, newOtp);
            if (!sent) {
                resp.setStatus(502);
                out.print("{\"error\":\"Failed to send OTP email\"}");
                return;
            }

            OtpCache.applyResend(input.email, newOtp);

            out.print("{\"status\":\"otp_resent\",\"message\":\"OTP has been resent to your email\"}");
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