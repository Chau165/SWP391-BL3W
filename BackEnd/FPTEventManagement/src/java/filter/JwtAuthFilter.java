package filter;

import utils.JwtUtils;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter(urlPatterns = {"/api/*"})
public class JwtAuthFilter implements Filter {

    // ==================== CORS ====================
    private void setCors(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");
        boolean allowed = isAllowedOrigin(origin);

        if (allowed) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
            resp.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "null");
        }

        resp.setHeader("Access-Control-Max-Age", "86400");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        resp.setHeader("Access-Control-Expose-Headers", "Authorization");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Vary", "Origin");
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        String o = origin.toLowerCase();

        if (o.equals("http://localhost:5173") || o.equals("http://127.0.0.1:5173")) {
            return true;
        }

        if (o.endsWith(".ngrok-free.app") || o.endsWith(".ngrok.app")) {
            return true;
        }

        return false;
    }

    // ==================== PUBLIC PATHS ====================
    private boolean isPublicPath(HttpServletRequest req) {
        String path = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
            path = path.substring(ctx.length());
        }

        // Các API không cần JWT (đăng nhập / đăng ký / forgot pass)
        if (path.equals("/api/login")) {
            return true;
        }
        if (path.equals("/api/register")) {
            return true;
        }
        if (path.equals("/api/register/send-otp")) {
            return true;
        }
        if (path.equals("/api/register/verify-otp")) {
            return true;
        }
        if (path.equals("/api/register/resend-otp")) {
            return true;
        }
        if (path.equals("/api/reset-password")) {
            return true;
        }
        if (path.equals("/api/forgot-password")) {
            return true;
        }

        // ====== 🔓 CÁC API THANH TOÁN VNPAY – BỎ QUA JWT ======
        // Tạo URL thanh toán ticket (project mới)
        if (path.equals("/api/payment-ticket")) {
            return true;
        }

        // Callback khi thanh toán vé xong (ReturnUrl)
        if (path.equals("/api/buyTicket")) {
            return true;
        }

        // ✅ PUBLIC: danh sách sự kiện cho Guest (không cần đăng nhập)
        if (path.equals("/api/events")) {
            return true;
        }

        // ✅ (TUỲ CHỌN) nếu muốn Guest xem luôn chi tiết sự kiện
        if (path.equals("/api/events/detail")) {
            return true;
        }

        // Swagger & OpenAPI
        if (path.equals("/api/openapi.json")) {
            return true;
        }
        if (path.startsWith("/swagger-ui")) {
            return true;
        }

        return false;
    }

    // ==================== FILTER ====================
    @Override
    public void doFilter(ServletRequest r, ServletResponse s, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) r;
        HttpServletResponse resp = (HttpServletResponse) s;

        setCors(req, resp);

        System.out.println("🔹 [JWT FILTER] Request: " + req.getRequestURI());

        // Cho OPTIONS (preflight) đi qua
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Bỏ qua JWT cho các path public
        if (isPublicPath(req)) {
            System.out.println("🔹 Public path, skip JWT check");
            chain.doFilter(req, resp);
            return;
        }

        // Từ đây trở đi: mọi /api/* đều cần JWT
        String auth = req.getHeader("Authorization");
        System.out.println("🔹 Authorization header: " + auth);

        if (auth == null || !auth.startsWith("Bearer ")) {
            System.out.println("❌ Missing or invalid Authorization header");
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"error\":\"Missing token\"}");
            return;
        }

        String token = auth.substring(7).trim();
        System.out.println("🔹 Token prefix: "
                + token.substring(0, Math.min(20, token.length())) + "...");

        if (!JwtUtils.validateToken(token)) {
            System.out.println("❌ Token validation failed");
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"error\":\"Invalid or expired token\"}");
            return;
        }

        try {
            String email = JwtUtils.getEmailFromToken(token);
            String role = JwtUtils.getRoleFromToken(token);
            Integer id = JwtUtils.getIdFromToken(token);

            // Chuẩn hóa role: bỏ ROLE_ và uppercase
            if (role != null) {
                role = role.toUpperCase();
                if (role.startsWith("ROLE_")) {
                    role = role.substring(5);
                }
            }

            System.out.println("✅ Token parsed: email=" + email + ", role=" + role + ", id=" + id);

            if (email == null || role == null || id == null) {
                throw new Exception("Missing claims in token");
            }

            // Gắn thông tin vào request
            req.setAttribute("jwt_email", email);
            req.setAttribute("jwt_role", role);
            req.setAttribute("jwt_id", id);

            // Quan trọng: cho controller cũ dùng tên 'role'
            req.setAttribute("role", role);
            req.setAttribute("userId", id);

            chain.doFilter(req, resp);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("❌ Error parsing token claims: " + e.getMessage());
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"error\":\"Invalid token claims\"}");
        }
    }

    private void writeJson(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(body);
        resp.getWriter().flush();
    }
}
