package controller;

import DAO.UsersDAO;
import DTO.LoginRequest;
import DTO.Users;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import utils.JwtUtils;
import utils.RecaptchaUtils;

@WebServlet("/api/login")
public class loginController extends HttpServlet {

    private final UsersDAO usersDAO = new UsersDAO();
    private final Gson gson = new Gson();

    // ==================== OPTIONS ====================
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT); // 204
    }

    // ==================== POST ====================
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        try (BufferedReader reader = request.getReader(); PrintWriter out = response.getWriter()) {

            LoginRequest loginReq = gson.fromJson(reader, LoginRequest.class);

            if (loginReq == null || isBlank(loginReq.getEmail()) || isBlank(loginReq.getPassword())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(jsonFail("Thiếu email hoặc mật khẩu", "AUTH_MISSING_FIELD"));
                out.flush();
                return;
            }

            // Verify reCAPTCHA
            if (!RecaptchaUtils.verify(loginReq.getRecaptchaToken())) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid reCAPTCHA\"}");
                out.flush();
                return;
            }

            // ✅ checkLogin giờ đã tự verify password hash
            Users user = usersDAO.checkLogin(loginReq.getEmail().trim(), loginReq.getPassword());

            if (user != null) {

                // DB đang dùng status: 'ACTIVE','INACTIVE','BLOCKED'
                if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print(jsonFail("Tài khoản bị khóa", "AUTH_BLOCKED"));
                    out.flush();
                    return;
                }

                // Tạo JWT token
                String token = JwtUtils.generateToken(user.getEmail(), user.getRole(), user.getId());
                System.out.println("Token User: " + token);

                response.setStatus(HttpServletResponse.SC_OK);
                LoginResponse payload = new LoginResponse("success", token, user);
                out.print(gson.toJson(payload));
                out.flush();
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print(jsonFail("Email hoặc mật khẩu không hợp lệ", "AUTH_INVALID"));
                out.flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.print(jsonError("Lỗi server: " + e.getMessage()));
                out.flush();
            }
        }
    }

    // ==================== CORS ====================
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

    // ==================== Helpers ====================
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String jsonFail(String message, String code) {
        return "{\"status\":\"fail\",\"code\":\"" + escape(code == null ? "" : code)
                + "\",\"message\":\"" + escape(message == null ? "" : message) + "\"}";
    }

    private String jsonError(String message) {
        return "{\"status\":\"error\",\"message\":\"" + escape(message == null ? "" : message) + "\"}";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ==================== DTOs ====================
    private static class LoginResponse {

        String status;
        String token;
        Users user;

        public LoginResponse(String status, String token, Users user) {
            this.status = status;
            this.token = token;
            this.user = user;
        }
    }
}
