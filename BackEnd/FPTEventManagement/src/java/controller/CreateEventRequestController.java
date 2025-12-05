package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.Timestamp;

@WebServlet("/api/event-requests")
public class CreateEventRequestController extends HttpServlet {

    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();
    private final Gson gson = new Gson();

    // DTO nhận body from FE
    private static class CreateReqBody {
        String title;
        String description;
        String preferredStartTime; // ví dụ: "2025-12-10T09:00:00.134Z" hoặc "2025-12-10T09:00"
        String preferredEndTime;
        Integer expectedCapacity;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ========= 1. Lấy token & kiểm tra role =========
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
        Integer userId = JwtUtils.getIdFromToken(token);

        if (userId == null || role == null || !"STUDENT".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ STUDENT mới được tạo request sự kiện\"}");
            return;
        }

        // ========= 2. Đọc body JSON =========
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        CreateReqBody body = gson.fromJson(sb.toString(), CreateReqBody.class);

        if (body == null || isBlank(body.title)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Title không được để trống\"}");
            return;
        }

        if (isBlank(body.preferredStartTime) || isBlank(body.preferredEndTime)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian bắt đầu / kết thúc không được để trống\"}");
            return;
        }

        // ========= 3. Parse & validate thời gian =========
        Timestamp startTime = parseDateTime(body.preferredStartTime);
        Timestamp endTime   = parseDateTime(body.preferredEndTime);

        if (startTime == null || endTime == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Định dạng thời gian không hợp lệ\"}");
            return;
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Không cho chọn quá khứ
        if (startTime.before(now)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian bắt đầu phải ở hiện tại hoặc tương lai\"}");
            return;
        }

        if (endTime.before(startTime)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian kết thúc phải sau thời gian bắt đầu\"}");
            return;
        }

        // ========= 4. Map vào DTO EventRequest =========
        EventRequest er = new EventRequest();
        er.setRequesterId(userId);
        er.setTitle(body.title.trim());
        er.setDescription(body.description != null ? body.description.trim() : null);
        er.setPreferredStartTime(startTime);
        er.setPreferredEndTime(endTime);
        er.setExpectedCapacity(body.expectedCapacity);
        er.setStatus("PENDING");

        // ========= 5. Insert DB =========
        Integer newId = eventRequestDAO.insertRequest(er);
        if (newId == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không tạo được request sự kiện\"}");
            return;
        }

        // ========= 6. Trả kết quả =========
        resp.setStatus(HttpServletResponse.SC_CREATED);
        out.print("{\"status\":\"success\",\"message\":\"Tạo request sự kiện thành công\",\"requestId\":" + newId + "}");
    }

    /**
     * Parse datetime từ FE:
     * - Hỗ trợ: "2025-12-10T09:00:00"
     * - Hỗ trợ: "2025-12-10T09:00:00.134Z"
     * - Hỗ trợ: "2025-12-10T09:00"
     */
    private Timestamp parseDateTime(String s) {
        if (s == null) return null;
        String value = s.trim();
        if (value.isEmpty()) return null;

        // Bỏ chữ Z nếu có (ISO UTC) -> ta coi như giờ local
        if (value.endsWith("Z") || value.endsWith("z")) {
            value = value.substring(0, value.length() - 1);
        }

        // Thay 'T' thành space để hợp với Timestamp.valueOf
        value = value.replace('T', ' ');

        // Nếu chỉ có yyyy-MM-dd HH:mm thì thêm :00
        // VD: "2025-12-10 09:00" (16 ký tự)
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

    // CORS giống mấy controller khác của bạn
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
