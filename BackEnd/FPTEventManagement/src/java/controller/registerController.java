package controller;

import DAO.UsersDAO;
import DTO.Users;
import com.google.gson.Gson;
import mylib.ValidationUtil;
import utils.JwtUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/register")
public class registerController extends HttpServlet {

    // Helper cho Java 8 thay cho String.isBlank()
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 🔑 Bảo đảm UTF-8 cho inbound/outbound
        req.setCharacterEncoding("UTF-8");
        setCorsHeaders(resp, req);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        try (BufferedReader reader = req.getReader(); PrintWriter out = resp.getWriter()) {
            // Parse payload that includes raw password (Users DTO doesn't contain password)
            class RegisterPayload {
                String fullName;
                String email;
                String phone;
                String password;
                String role;
                String status;
            }

            RegisterPayload payload = gson.fromJson(reader, RegisterPayload.class);
            if (payload == null) {
                resp.setStatus(400);
                out.print("{\"error\":\"Invalid input\"}");
                return;
            }

            // Basic validation
            if (!ValidationUtil.isValidFullName(payload.fullName)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Full name is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidVNPhone(payload.phone)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Phone number is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidEmail(payload.email)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Email is invalid\"}");
                return;
            }
            if (!ValidationUtil.isValidPassword(payload.password)) {
                resp.setStatus(400);
                out.print("{\"error\":\"Password must be at least 6 characters, include letters and digits\"}");
                return;
            }

            UsersDAO dao = new UsersDAO();

            try {
                if (dao.existsByEmail(payload.email)) {
                    resp.setStatus(409);
                    out.print("{\"error\":\"Email already exists\"}");
                    return;
                }

                // Build Users entity (Users doesn't include raw password)
                Users toInsert = new Users();
                toInsert.setFullName(payload.fullName);
                toInsert.setEmail(payload.email);
                toInsert.setPhone(payload.phone);
                toInsert.setRole(payload.role != null ? payload.role : "Driver");
                toInsert.setStatus(!isBlank(payload.status) ? payload.status : "Active");

                int newId = dao.insertUser(toInsert, payload.password);
            if (newId <= 0) {
                resp.setStatus(400);
                out.print("{\"error\":\"Failed to create user\"}");
                return;
            }

            // ✅ Lấy lại user mới để sinh token
            Users newUser = dao.findById(newId);
            if (newUser == null) {
                resp.setStatus(500);
                out.print("{\"error\":\"User created but cannot load profile\"}");
                return;
            }

            // ✅ Sinh token JWT và trả luôn cho FE
            String token = JwtUtils.generateToken(newUser.getEmail(), newUser.getRole(), newUser.getId());

            resp.setStatus(200);
            out.print("{"
                    + "\"status\":\"success\","
                    + "\"message\":\"Registered and logged in successfully\","
                    + "\"token\":\"" + token + "\"," 
                    + "\"user\":" + gson.toJson(newUser)
                    + "}");
            } catch (Exception e) {
                e.printStackTrace();
                resp.setStatus(500);
                out.print("{\"error\":\"Internal server error\"}");
                return;
            }
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
