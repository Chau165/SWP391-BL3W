package controller;

import DAO.UsersDAO;
import com.google.gson.Gson;
import mylib.EmailService;
import mylib.OtpCache;
import mylib.ValidationUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register/send-otp")
public class RegisterSendOtpController extends HttpServlet {

    private final Gson gson = new Gson();

    static class RegisterRequest {

        String fullName;
        String phone;
        String email;
        String password;
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
        req.setCharacterEncoding("UTF-8");

        try ( BufferedReader reader = req.getReader();  PrintWriter out = resp.getWriter()) {

            RegisterRequest input = gson.fromJson(reader, RegisterRequest.class);
            if (input == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            // Validate
            if (!ValidationUtil.isValidFullName(input.fullName)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Full name is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidVNPhone(input.phone)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Phone number is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidEmail(input.email)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Email is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidPassword(input.password)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Password must be at least 6 characters, include letters and digits\"}");
                return;
            }

            UsersDAO dao = new UsersDAO();
            if (dao.existsByEmail(input.email)) {
                resp.setStatus(409);
                out.print("{\"error\":\"Email already exists\"}");
                return;
            }

            // Sinh & lưu OTP tạm
            String otp = EmailService.generateOtp();
            OtpCache.put(input.email, input.fullName, input.phone, input.password, otp);
            System.out.println("[send-otp] ✅ Generated OTP " + otp + " for " + input.email);

            boolean sent = EmailService.sendRegistrationOtpEmail(input.email, otp);
            if (!sent) {
                resp.setStatus(502);
                out.print("{\"error\":\"Failed to send OTP email\"}");
                return;
            }

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
