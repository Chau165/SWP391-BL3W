package controller;

import DAO.TicketDAO;
import DTO.EventStatsResponse;
import utils.JwtUtils;
import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/api/events/stats")
public class EventStatsController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final Gson gson = new Gson();

    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null && (origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains("ngrok"))) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        }
        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Max-Age", "86400");
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        try {
            // 1. JWT Authentication & Validation
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"error\":\"Missing or invalid Authorization header\"}");
                return;
            }

            String token = authHeader.substring(7);
            if (!JwtUtils.validateToken(token)) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"error\":\"Invalid or expired token\"}");
                return;
            }

            // 2. Lấy Role và UserId từ Token
            String role = JwtUtils.getRoleFromToken(token);
            int userId = JwtUtils.getIdFromToken(token); 

            if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("STAFF") || role.equalsIgnoreCase("ADMIN"))) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print("{\"error\":\"Permission denied.\"}");
                return;
            }

            // 3. Lấy và kiểm tra tham số eventId
            String eventIdStr = req.getParameter("eventId");
            if (eventIdStr == null || eventIdStr.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"Missing eventId parameter\"}");
                return;
            }

            int eventId;
            try {
                eventId = Integer.parseInt(eventIdStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\":\"eventId must be a number\"}");
                return;
            }

            // 4. KIỂM TRA SỰ TỒN TẠI CỦA EVENT (Dành cho cả Admin và Organizer)
            // Bạn cần thêm hàm checkEventExists vào TicketDAO như đã trao đổi
            boolean exists = ticketDAO.checkEventExists(eventId);
            if (!exists) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // Trả về 404
                out.print("{\"error\":\"no data found\"}");
                return;
            }

            // 5. Nếu Event tồn tại, tiến hành lấy số liệu theo phân quyền
            EventStatsResponse stats = ticketDAO.getEventStatsByRole(role, userId, eventId);

            // 6. Kiểm tra quyền sở hữu đối với ORGANIZER
            // Nếu Event có tồn tại nhưng Organizer này không tạo (totalRegistered = 0 do SQL JOIN chặn)
            if (role.equalsIgnoreCase("ORGANIZER") && stats.getTotalRegistered() == 0) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // Trả về 403
                out.print("{\"error\":\"You do not have permission to view this event's statistics.\"}");
                return;
            }

            // 7. Trả về kết quả thành công
            resp.setStatus(HttpServletResponse.SC_OK);
            out.print(gson.toJson(stats));

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}