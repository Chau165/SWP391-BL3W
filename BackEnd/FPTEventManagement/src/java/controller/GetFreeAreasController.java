package controller;

import DAO.VenueAreaDAO;
import DTO.VenueArea;
import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/areas/free")
public class GetFreeAreasController extends HttpServlet {

    private final VenueAreaDAO venueAreaDAO = new VenueAreaDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // 1) Check JWT & role (ORGANIZER / ADMIN)
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null ||
                !( "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role) )) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ ORGANIZER hoặc ADMIN mới được xem area trống\"}");
            return;
        }

        // 2) Lấy query param startTime & endTime
        String startStr = req.getParameter("startTime");
        String endStr   = req.getParameter("endTime");

        if (isBlank(startStr) || isBlank(endStr)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu startTime hoặc endTime\"}");
            return;
        }

        // 3) Parse & validate time
        Timestamp start = parseDateTime(startStr);
        Timestamp end   = parseDateTime(endStr);

        if (start == null || end == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Định dạng thời gian không hợp lệ\"}");
            return;
        }

        if (!end.after(start)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"endTime phải sau startTime\"}");
            return;
        }

        // (Optional) Không cho chọn quá khứ
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (start.before(now)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"startTime phải là hiện tại hoặc tương lai\"}");
            return;
        }

        // 4) Query DB lấy danh sách Area còn trống với buffer 1h
        List<VenueArea> areas = venueAreaDAO.getFreeAreasWith1hBuffer(start, end);

        // 5) Build response JSON
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("startTime", start.toString());
        result.put("endTime", end.toString());
        result.put("bufferHours", 1);
        result.put("total", areas.size());
        result.put("areas", areas);

        out.print(gson.toJson(result));
    }

    // ====== Helper parse datetime (giống controller khác) ======
    private Timestamp parseDateTime(String s) {
        if (s == null) return null;
        String value = s.trim();
        if (value.isEmpty()) return null;

        // Bỏ 'Z' nếu có
        if (value.endsWith("Z") || value.endsWith("z")) {
            value = value.substring(0, value.length() - 1);
        }

        // Thay 'T' thành space
        value = value.replace('T', ' ');

        // Nếu chỉ yyyy-MM-dd HH:mm -> thêm :00
        if (value.length() == 16) {
            value = value + ":00";
        }

        try {
            return Timestamp.valueOf(value);
        } catch (Exception e) {
            System.err.println("[WARN] parseDateTime failed for value = " + s + " -> " + e.getMessage());
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

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
