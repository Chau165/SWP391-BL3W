package controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import utils.JwtUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@WebServlet("/api/admin/config/system")
public class StaffSystemConfigController extends HttpServlet {

    private final Gson gson = new Gson();

    // ✅ File runtime trong webapp
    private static final String CLASSPATH_RELATIVE = "/WEB-INF/classes/config/SystemConfig.json";

    // ✅ Fallback: dev path (chỉ dùng khi getRealPath == null)
    private static final String ABSOLUTE_DEV_PATH =
            "C:\\Users\\Surface\\SWP391-BL3W-main\\SWP391-BL3W-main\\SWP391-BL3W-main\\BackEnd\\FPTEventManagement\\src\\java\\config\\SystemConfig.json";

    public static class SystemConfig {
        public int minMinutesAfterStart;                 // checkout: sau start X phút
        public int checkinAllowedBeforeStartMinutes;     // checkin: trước start X phút
    }

    private SystemConfig defaultCfg() {
        SystemConfig cfg = new SystemConfig();
        cfg.minMinutesAfterStart = 60;
        cfg.checkinAllowedBeforeStartMinutes = 60;
        return cfg;
    }

    private boolean canViewConfig(String role) {
        return role != null && "STAFF".equalsIgnoreCase(role);
    }

    private boolean canUpdateConfig(String role) {
        return role != null && "STAFF".equalsIgnoreCase(role);
    }

    private Path resolveConfigPath(HttpServletRequest req) {
        String realPath = req.getServletContext().getRealPath(CLASSPATH_RELATIVE);
        if (realPath != null) return Paths.get(realPath);
        return Paths.get(ABSOLUTE_DEV_PATH);
    }

    private String requireRole(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Vui lòng đăng nhập\"}");
            return null;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Phiên đăng nhập đã hết hạn\"}");
            return null;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || role.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Không xác định được role\"}");
            return null;
        }

        return role;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String role = requireRole(req, resp);
        if (role == null) return;

        if (!canViewConfig(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Bạn không có quyền xem cấu hình hệ thống\"}");
            return;
        }

        // ✅ Đọc đúng file runtime (cùng nơi POST ghi)
        SystemConfig cfg;
        try {
            Path path = resolveConfigPath(req);
            if (Files.exists(path)) {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                cfg = gson.fromJson(json, SystemConfig.class);
            } else {
                cfg = null;
            }

            if (cfg == null) cfg = defaultCfg();

            // fill default nếu thiếu field (JSON cũ)
            if (cfg.minMinutesAfterStart < 0 || cfg.minMinutesAfterStart > 600) cfg.minMinutesAfterStart = 60;
            if (cfg.checkinAllowedBeforeStartMinutes < 0 || cfg.checkinAllowedBeforeStartMinutes > 600)
                cfg.checkinAllowedBeforeStartMinutes = 60;

        } catch (Exception e) {
            cfg = defaultCfg();
        }

        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.add("data", gson.toJsonTree(cfg));

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(res));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String role = requireRole(req, resp);
        if (role == null) return;

        if (!canUpdateConfig(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Bạn không có quyền cập nhật cấu hình hệ thống\"}");
            return;
        }

        SystemConfig newCfg = gson.fromJson(req.getReader(), SystemConfig.class);

        if (newCfg == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Body không hợp lệ\"}");
            return;
        }

        // ✅ Validate cả 2 field
        if (newCfg.minMinutesAfterStart < 0 || newCfg.minMinutesAfterStart > 600) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"minMinutesAfterStart phải từ 0 đến 600\"}");
            return;
        }
        if (newCfg.checkinAllowedBeforeStartMinutes < 0 || newCfg.checkinAllowedBeforeStartMinutes > 600) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"checkinAllowedBeforeStartMinutes phải từ 0 đến 600\"}");
            return;
        }

        try {
            Path path = resolveConfigPath(req);
            if (path.getParent() != null) Files.createDirectories(path.getParent());

            String json = gson.toJson(newCfg);
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));

            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Cập nhật cấu hình thành công");
            res.add("data", gson.toJsonTree(newCfg));
            res.addProperty("writtenTo", path.toAbsolutePath().toString());

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(gson.toJson(res));

        } catch (Exception ex) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Không thể lưu cấu hình. Hãy kiểm tra đường dẫn/permission.\"}");
        }
    }
}
