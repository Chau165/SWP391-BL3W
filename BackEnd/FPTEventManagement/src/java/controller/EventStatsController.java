package controller;

import DAO.TicketDAO;
import DTO.EventStatsResponse; // DTO mới có đủ trường booking/refunded
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@WebServlet("/api/events/stats")
public class EventStatsController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final Gson gson = new Gson();

    // Giữ nguyên logic CORS của bạn
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
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        // 1. JWT Authentication (Giữ nguyên logic cũ của bạn)
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        // 2. Authorization (Chỉ cho phép ORGANIZER hoặc STAFF)
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("STAFF") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Permission denied. ORGANIZER, STAFF or ADMIN only.\"}");
            return;
        }

        // 3. Lấy tham số eventId
        String eventIdStr = req.getParameter("eventId");
        if (eventIdStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing eventId parameter\"}");
            return;
        }

        int eventId;
        try {
            eventId = Integer.parseInt(eventIdStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"eventId must be a number\"}");
            return;
        }

        // 4. Lấy tham số date (Mới thêm)
        String dateParam = req.getParameter("date");
        LocalDate filterDate = null;
        if (dateParam != null && !dateParam.trim().isEmpty()) {
            try {
                filterDate = LocalDate.parse(dateParam);
            } catch (DateTimeParseException e) {
                // Nếu ngày sai định dạng thì bỏ qua lọc ngày, không return lỗi
            }
        }

        // 5. Gọi TicketDAO (Hàm mới có tham số filterDate)
        // Lưu ý: Đảm bảo TicketDAO đã có hàm getEventStats(int, LocalDate) trả về EventStatsResponse
        EventStatsResponse stats = ticketDAO.getEventStats(eventId);

        if (stats == null) {
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"No data found\"}");
            return;
        }

        // Đảm bảo trả về đúng DTO có đủ các trường bạn yêu cầu
        resp.setStatus(200);
        resp.getWriter().write(gson.toJson(stats));
    }
}
