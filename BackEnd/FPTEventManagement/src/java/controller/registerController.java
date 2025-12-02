package controller;

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.ValidationUtil;
import utils.JwtUtils;
import utils.PasswordUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register")
public class registerController extends HttpServlet {

    private final Gson gson = new Gson();

    // Helper cho Java 8 (thay cho String.isBlank)
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ====== DTO request chỉ dùng cho raw JSON từ FE ======
    private static class RegisterRequest {
        String fullName;
        String phone;
        String email;
        String password; // mật khẩu chưa hash
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // UTF-8 cho request/response
        req.setCharacterEncoding("UTF-8");
        setCorsHeaders(resp, req);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        try (BufferedReader reader = req.getReader();
             PrintWriter out = resp.getWriter()) {

            RegisterRequest input = gson.fromJson(reader, RegisterRequest.class);

            if (input == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            // ====== Validate ======
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

            // Email đã tồn tại chưa?
            if (dao.existsByEmail(input.email)) {
                resp.setStatus(409);
                out.print("{\"error\":\"Email already exists\"}");
                return;
            }

            // ====== Tạo entity Users theo DB mới ======
            Users u = new Users();
            u.setFullName(input.fullName);
            u.setPhone(input.phone);
            u.setEmail(input.email);

            // Hash password trước khi lưu
            String hash = PasswordUtils.hashPassword(input.password);
            u.setPasswordHash(hash);

            // Mặc định role & status
            u.setRole("STUDENT");
            u.setStatus("ACTIVE");

            // ====== Insert DB ======
            int newId = dao.insertUser(u);
            if (newId <= 0) {
                resp.setStatus(400);
                out.print("{\"error\":\"Failed to create user\"}");
                return;
            }

            // Lấy lại user mới
            Users newUser = dao.findById(newId);
            if (newUser == null) {
                resp.setStatus(500);
                out.print("{\"error\":\"User created but cannot load profile\"}");
                return;
            }

            // Sinh JWT
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
