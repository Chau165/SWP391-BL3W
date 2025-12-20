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

    // ✅ File runtime trong webapp:
    // Khi deploy lên Tomcat, file config thực sự nằm trong webapp (WEB-INF/classes/...)
    // GET sẽ đọc file này, POST cũng sẽ ghi vào chính file này (nếu getRealPath != null)
    private static final String CLASSPATH_RELATIVE = "/WEB-INF/classes/config/SystemConfig.json";

    // ✅ Fallback: dev path (chỉ dùng khi getRealPath == null)
    // THƯỜNG xảy ra khi chạy kiểu packaged WAR/JAR hoặc server không cho truy cập realPath.
    // LƯU Ý: Hardcode path chỉ phù hợp dev, deploy thật nên bỏ hoặc cấu hình bằng env.
    private static final String ABSOLUTE_DEV_PATH =
            "C:\\Users\\Surface\\SWP391-BL3W-main\\SWP391-BL3W-main\\SWP391-BL3W-main\\BackEnd\\FPTEventManagement\\src\\java\\config\\SystemConfig.json";

    /**
     * DTO cấu hình hệ thống:
     * - minMinutesAfterStart:
     *      checkout: chỉ cho check-out sau startTime X phút
     * - checkinAllowedBeforeStartMinutes:
     *      checkin: cho check-in trước startTime X phút
     */
    public static class SystemConfig {
        public int minMinutesAfterStart;                 // checkout: sau start X phút
        public int checkinAllowedBeforeStartMinutes;     // checkin: trước start X phút
    }

    /**
     * defaultCfg():
     * - Nếu file không tồn tại / JSON lỗi / thiếu field => dùng default để hệ thống vẫn chạy.
     */
    private SystemConfig defaultCfg() {
        SystemConfig cfg = new SystemConfig();
        cfg.minMinutesAfterStart = 60;
        cfg.checkinAllowedBeforeStartMinutes = 60;
        return cfg;
    }

    /**
     * canViewConfig(role):
     * - Ai được quyền xem config?
     * - Hiện tại bạn đang cho STAFF xem.
     *
     * CÂU HỎI THẦY/CÔ:
     * - "URL là /api/admin/... nhưng check role lại STAFF, có mâu thuẫn không?"
     *   => Trả lời: đây là naming route, còn rule quyền do backend kiểm soát,
     *      (có thể đổi sau: STAFF/ADMIN đều xem được).
     */
    private boolean canViewConfig(String role) {
        return role != null && "STAFF".equalsIgnoreCase(role);
    }

    /**
     * canUpdateConfig(role):
     * - Ai được quyền cập nhật config?
     * - Hiện tại bạn cũng cho STAFF update.
     */
    private boolean canUpdateConfig(String role) {
        return role != null && "STAFF".equalsIgnoreCase(role);
    }

    /**
     * resolveConfigPath(req):
     * - Tìm đường dẫn file config thực tế để đọc/ghi.
     * - Ưu tiên: ServletContext.getRealPath(CLASSPATH_RELATIVE)
     * - Nếu server trả null => dùng ABSOLUTE_DEV_PATH (fallback).
     *
     * CÂU HỎI THẦY/CÔ:
     * 1) "getRealPath là gì?" => Là đường dẫn vật lý trên ổ đĩa tương ứng với resource trong webapp.
     * 2) "Khi nào getRealPath trả null?" => khi deploy dạng không explode / hoặc container hạn chế.
     */
    private Path resolveConfigPath(HttpServletRequest req) {
        String realPath = req.getServletContext().getRealPath(CLASSPATH_RELATIVE);
        if (realPath != null) return Paths.get(realPath);
        return Paths.get(ABSOLUTE_DEV_PATH);
    }

    /**
     * requireRole(req, resp):
     * - Kiểm tra JWT:
     *    + Có header Authorization dạng Bearer <token>?
     *    + token hợp lệ?
     *    + trích role từ token?
     *
     * Nếu fail -> trả lỗi ngay và return null.
     *
     * ======================= STATUS CODE TRẢ VỀ TRONG requireRole =======================
     * - 401 UNAUTHORIZED:
     *    + Thiếu Authorization header
     *    + Token không hợp lệ / hết hạn
     * - 403 FORBIDDEN:
     *    + Không lấy được role (token không chứa role hoặc role rỗng)
     *
     * CÂU HỎI THẦY/CÔ:
     * - "401 và 403 khác nhau thế nào?"
     *   => 401: chưa xác thực (chưa login / token sai)
     *   => 403: đã xác thực nhưng không đủ quyền (hoặc role không hợp lệ)
     */
    private String requireRole(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String authHeader = req.getHeader("Authorization");

        // Thiếu token / không đúng format Bearer => 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            resp.getWriter().write("{\"error\":\"Vui lòng đăng nhập\"}");
            return null;
        }

        // cắt token
        String token = authHeader.substring(7);

        // token hết hạn/không hợp lệ => 401
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            resp.getWriter().write("{\"error\":\"Phiên đăng nhập đã hết hạn\"}");
            return null;
        }

        // lấy role
        String role = JwtUtils.getRoleFromToken(token);

        // role null/rỗng => 403 (không xác định quyền)
        if (role == null || role.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            resp.getWriter().write("{\"error\":\"Không xác định được role\"}");
            return null;
        }

        return role;
    }

    /**
     * ======================= GET /api/admin/config/system =======================
     * Mục đích:
     * - STAFF xem cấu hình hiện tại.
     *
     * Flow:
     * 1) set content-type JSON
     * 2) requireRole() -> check token + lấy role
     * 3) canViewConfig(role) -> check quyền xem
     * 4) đọc file config runtime -> parse JSON -> fallback default nếu lỗi
     * 5) trả JSON { success: true, data: cfg }
     *
     * ======================= STATUS CODE TRẢ VỀ (GET) =======================
     * - 200 OK:
     *    + Xem config thành công (kể cả file lỗi -> vẫn trả default)
     * - 401 UNAUTHORIZED:
     *    + Thiếu token hoặc token invalid (từ requireRole)
     * - 403 FORBIDDEN:
     *    + role không hợp lệ (từ requireRole)
     *    + hoặc không có quyền xem (canViewConfig == false)
     *
     * LƯU Ý:
     * - Khi đọc file lỗi -> bạn catch và dùng defaultCfg() => vẫn trả 200 OK.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // 1) Auth: lấy role từ token (nếu fail đã set status và write error)
        String role = requireRole(req, resp);
        if (role == null) return;

        // 2) Authorization: kiểm tra quyền xem
        if (!canViewConfig(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            resp.getWriter().write("{\"error\":\"Bạn không có quyền xem cấu hình hệ thống\"}");
            return;
        }

        // 3) Đọc đúng file runtime (cùng nơi POST ghi)
        SystemConfig cfg;
        try {
            Path path = resolveConfigPath(req);

            // Nếu file tồn tại -> đọc JSON -> parse thành SystemConfig
            if (Files.exists(path)) {
                String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                cfg = gson.fromJson(json, SystemConfig.class);
            } else {
                // file không có -> cfg null để fallback default
                cfg = null;
            }

            // Nếu parse ra null -> default
            if (cfg == null) cfg = defaultCfg();

            // 4) Fill default nếu thiếu field / giá trị không hợp lệ (bảo vệ system)
            // Ví dụ JSON bị sửa thành -1 hoặc 99999 => reset về 60
            if (cfg.minMinutesAfterStart < 0 || cfg.minMinutesAfterStart > 600) cfg.minMinutesAfterStart = 60;

            if (cfg.checkinAllowedBeforeStartMinutes < 0 || cfg.checkinAllowedBeforeStartMinutes > 600)
                cfg.checkinAllowedBeforeStartMinutes = 60;

        } catch (Exception e) {
            // Nếu lỗi I/O hoặc JSON parse lỗi => dùng default (hệ thống không chết)
            cfg = defaultCfg();
        }

        // 5) Build response JSON
        JsonObject res = new JsonObject();
        res.addProperty("success", true);
        res.add("data", gson.toJsonTree(cfg));

        resp.setStatus(HttpServletResponse.SC_OK); // 200
        resp.getWriter().write(gson.toJson(res));
    }

    /**
     * ======================= POST /api/admin/config/system =======================
     * Mục đích:
     * - STAFF cập nhật config hệ thống bằng JSON body.
     *
     * Body JSON (ví dụ):
     * {
     *   "minMinutesAfterStart": 30,
     *   "checkinAllowedBeforeStartMinutes": 45
     * }
     *
     * Flow:
     * 1) requireRole() -> check token + role
     * 2) canUpdateConfig(role) -> check quyền update
     * 3) parse body JSON -> SystemConfig newCfg
     * 4) validate range 0..600 cho cả 2 field
     * 5) resolve path -> createDirectories -> write file JSON
     * 6) trả JSON success + data + writtenTo
     *
     * ======================= STATUS CODE TRẢ VỀ (POST) =======================
     * - 200 OK:
     *    + Update config thành công + ghi file thành công
     *
     * - 400 BAD_REQUEST:
     *    + Body không hợp lệ (newCfg == null)
     *    + minMinutesAfterStart ngoài [0..600]
     *    + checkinAllowedBeforeStartMinutes ngoài [0..600]
     *
     * - 401 UNAUTHORIZED:
     *    + Thiếu token hoặc token invalid (từ requireRole)
     *
     * - 403 FORBIDDEN:
     *    + role null/rỗng (từ requireRole)
     *    + hoặc không có quyền update (canUpdateConfig == false)
     *
     * - 500 INTERNAL_SERVER_ERROR:
     *    + Không ghi được file (permission/path sai/IO error)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // 1) Auth
        String role = requireRole(req, resp);
        if (role == null) return;

        // 2) Authorization
        if (!canUpdateConfig(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            resp.getWriter().write("{\"error\":\"Bạn không có quyền cập nhật cấu hình hệ thống\"}");
            return;
        }

        // 3) Parse body JSON -> newCfg
        // req.getReader() đọc trực tiếp body request
        SystemConfig newCfg = gson.fromJson(req.getReader(), SystemConfig.class);

        // Nếu body null / JSON không parse được -> 400
        if (newCfg == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"Body không hợp lệ\"}");
            return;
        }

        // 4) Validate cả 2 field (bảo vệ hệ thống khỏi config xấu)
        if (newCfg.minMinutesAfterStart < 0 || newCfg.minMinutesAfterStart > 600) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"minMinutesAfterStart phải từ 0 đến 600\"}");
            return;
        }
        if (newCfg.checkinAllowedBeforeStartMinutes < 0 || newCfg.checkinAllowedBeforeStartMinutes > 600) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"checkinAllowedBeforeStartMinutes phải từ 0 đến 600\"}");
            return;
        }

        // 5) Ghi file config
        try {
            Path path = resolveConfigPath(req);

            // đảm bảo thư mục cha tồn tại
            if (path.getParent() != null) Files.createDirectories(path.getParent());

            // chuyển object -> json string
            String json = gson.toJson(newCfg);

            // ghi file (overwrite)
            Files.write(path, json.getBytes(StandardCharsets.UTF_8));

            // 6) Response success
            JsonObject res = new JsonObject();
            res.addProperty("success", true);
            res.addProperty("message", "Cập nhật cấu hình thành công");
            res.add("data", gson.toJsonTree(newCfg));
            res.addProperty("writtenTo", path.toAbsolutePath().toString()); // cho biết ghi vào đâu (debug)

            resp.setStatus(HttpServletResponse.SC_OK); // 200
            resp.getWriter().write(gson.toJson(res));

        } catch (Exception ex) {
            // Nếu không ghi được file -> 500
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            resp.getWriter().write("{\"error\":\"Không thể lưu cấu hình. Hãy kiểm tra đường dẫn/permission.\"}");
        }
    }
}
