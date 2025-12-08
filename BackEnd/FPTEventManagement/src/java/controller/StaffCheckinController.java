package controller;

import DAO.TicketDAO;
import DTO.Ticket;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.Timestamp;

@WebServlet("/api/staff/checkin")
public class StaffCheckinController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final Gson gson = new Gson();

    // Nếu bạn có CORS chung thì gọi lại hàm đó
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

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        // ===== 1. Lấy token từ header =====
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring(7); // bỏ "Bearer "
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Invalid or expired token\"}");
            return;
        }

        // ===== 2. Kiểm tra role: chỉ STAFF / ADMIN mới được check-in =====
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Permission denied. ORGANIZER only.\"}");
            return;
        }

        // ===== 3. Lấy ticketId từ query param (hoặc từ qrCodeValue tùy bạn) =====
        String ticketIdStr = req.getParameter("ticketId");
        if (ticketIdStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing ticketId\"}");
            return;
        }

        int ticketId;
        try {
            ticketId = Integer.parseInt(ticketIdStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"ticketId must be a number\"}");
            return;
        }

        // ===== 4. Tìm ticket =====
        Ticket ticket = ticketDAO.getTicketById(ticketId);
        if (ticket == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"Ticket not found\"}");
            return;
        }

        // Có thể bạn muốn check thêm điều kiện sự kiện, thời gian, v.v
        // Ví dụ chỉ cho checkin nếu status = BOOKED
        if (!"BOOKED".equalsIgnoreCase(ticket.getStatus())) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject json = new JsonObject();
            json.addProperty("error", "Ticket cannot be checked in");
            json.addProperty("currentStatus", ticket.getStatus());
            resp.getWriter().write(gson.toJson(json));
            return;
        }

        // ===== 5. Thực hiện check-in =====
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean ok = ticketDAO.checkinTicket(ticketId, now);

        if (!ok) {
            // Trường hợp này thường do status không phải BOOKED (do điều kiện WHERE)
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Check-in failed. Maybe already checked in or invalid status.\"}");
            return;
        }

        // ===== 6. Trả kết quả =====
        JsonObject resJson = new JsonObject();
        resJson.addProperty("message", "Check-in successful");
        resJson.addProperty("ticketId", ticketId);
        resJson.addProperty("status", "CHECKED_IN");
        resJson.addProperty("checkinTime", now.toString());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(resJson));
    }
}
