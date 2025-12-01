package controller;

import DAO.UsersDAO;
import com.google.gson.Gson;
import mylib.EmailService;
import mylib.OtpCache;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register/send-otp")
public class RegisterSendOtpController extends HttpServlet {

    private final Gson gson = new Gson();

    // ================= OPTIONS =================
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    // ================= DTO REQUEST =================
    static class RegisterRequest {
        String fullName;
        String phone;
        String email;
        String password;
    }

    // ================= POST =================
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        // === DEBUG: log thông tin context & URL ===
        String baseUrl = req.getScheme() + "://" + req.getServerName()
                + ((req.getServerPort() == 80 || req.getServerPort() == 443) ? "" : ":" + req.getServerPort())
                + req.getContextPath();
        System.out.println("=== [RegisterSendOtpController] ===");
        System.out.println("Request URL: " + baseUrl + req.getServletPath());

        try (BufferedReader reader = req.getReader(); PrintWriter out = resp.getWriter()) {

            // === Đọc raw body để debug ===
            StringBuilder raw = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) raw.append(line);
            System.out.println("[send-otp] Raw JSON body: " + raw);

            // === Parse JSON ===
            RegisterRequest input = gson.fromJson(raw.toString(), RegisterRequest.class);
            if (input == null || input.email == null || input.password == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input or missing email/password\"}");
                System.out.println("[send-otp] ❌ Invalid input");
                return;
            }

            System.out.println("[send-otp] Parsed: fullName=" + input.fullName
                    + ", phone=" + input.phone + ", email=" + input.email);

            // === Check email tồn tại (nếu cần) ===
            UsersDAO dao = new UsersDAO();
            try {
                if (dao.existsByEmail(input.email)) {
                    resp.setStatus(409);
                    out.print("{\"error\":\"Email already exists\"}");
                    System.out.println("[send-otp] ⚠ Email already exists: " + input.email);
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                resp.setStatus(500);
                out.print("{\"error\":\"Database error at existsByEmail\"}");
                return;
            }

            // === Sinh & lưu OTP ===
            String otp = EmailService.generateOtp();
            OtpCache.put(input.email, input.fullName, input.phone, input.password, otp);
            System.out.println("[send-otp] ✅ Generated OTP: " + otp);

            // === Gửi email OTP ===
            boolean sent = EmailService.sendRegistrationOtpEmail(input.email, otp);
            if (!sent) {
                System.err.println("[send-otp] ❌ Failed to send OTP email to " + input.email);
                // Tạm thời vẫn trả thành công để test FE
                // resp.setStatus(502);
                // out.print("{\"error\":\"Failed to send OTP email\"}");
                // return;
                out.print("{\"status\":\"otp_sent\",\"message\":\"(DEBUG MODE) OTP generated but email sending failed; check logs\"}");
                return;
            }

            System.out.println("[send-otp] ✅ Email sent successfully to: " + input.email);

            resp.setStatus(200);
            out.print("{\"status\":\"otp_sent\",\"message\":\"OTP has been sent to your email\"}");
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(500);
            resp.getWriter().print("{\"error\":\"Unhandled error in send-otp\"}");
        }
    }

    // ================= CORS =================
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
