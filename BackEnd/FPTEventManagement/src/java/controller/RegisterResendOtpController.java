package controller;

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
}
