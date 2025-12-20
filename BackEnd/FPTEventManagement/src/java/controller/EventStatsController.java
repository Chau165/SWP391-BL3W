package controller;

/**
 * ========================================================================================================
 * CONTROLLER: EventStatsController - THỐNG KÊ SỰ KIỆN
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Lấy thống kê chi tiết của 1 sự kiện
 * - Chỉ ORGANIZER hoặc STAFF có quyền xem thống kê
 * - Thống kê bao gồm: tổng đăng ký, check-in, check-out, tỉ lệ
 * - Dùng JWT authentication và role-based authorization
 * 
 * ENDPOINT: GET /api/events/stats?eventId=123
 * AUTHENTICATION: Required (JWT token)
 * AUTHORIZATION: ORGANIZER, STAFF only
 * 
 * REQUEST:
 * GET /api/events/stats?eventId=123
 * Headers:
 *   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
 * 
 * RESPONSE SUCCESS (200):
 * {
 *   "eventId": 123,
 *   "totalRegistered": 500,
 *   "totalCheckedIn": 350,
 *   "totalCheckedOut": 200,
 *   "checkInRate": 70.0,
 *   "checkOutRate": 40.0
 * }
 * 
 * RESPONSE ERROR:
 * - 400 Bad Request: Thiếu eventId, eventId không phải số
 * - 401 Unauthorized: Token không hợp lệ, hết hạn
 * - 403 Forbidden: User không phải ORGANIZER/STAFF
 * - 404 Not Found: Sự kiện không tồn tại
 * - 500 Internal Server Error: Lỗi database
 * 
 * LUỒNG XỬ LÝ:
 * 1. FE gửi GET request với JWT token và eventId
 * 2. Extract JWT token từ Authorization header
 * 3. Verify JWT token (JwtUtils.verifyToken)
 * 4. Parse claims để lấy userId và role
 * 5. Kiểm tra role = ORGANIZER hoặc STAFF
 * 6. Nếu không phải -> 403 Forbidden
 * 7. Parse eventId từ query parameter
 * 8. Validate eventId là số nguyên dương
 * 9. Gọi TicketDAO.getEventStats(eventId)
 * 10. DAO thực hiện query thống kê:
 *     - COUNT registrations WHERE eventId
 *     - COUNT check-in WHERE status = CHECKED_IN
 *     - COUNT check-out WHERE status = CHECKED_OUT
 *     - Tính tỉ lệ check-in/check-out
 * 11. Trả về EventStatsDTO
 * 
 * THỐNG KÊ (EventStatsDTO):
 * - eventId: ID của sự kiện
 * - totalRegistered: Tổng số người đăng ký tham gia
 * - totalCheckedIn: Số người đã check-in
 * - totalCheckedOut: Số người đã check-out
 * - checkInRate: Tỉ lệ check-in (totalCheckedIn / totalRegistered * 100)
 * - checkOutRate: Tỉ lệ check-out (totalCheckedOut / totalRegistered * 100)
 * 
 * ROLES:
 * - ORGANIZER: Người tạo sự kiện, xem thống kê tất cả sự kiện của mình
 * - STAFF: Nhân viên quản lý sự kiện, xem thống kê các sự kiện được giao
 * - ATTENDEE: Người tham gia, KHÔNG được xem thống kê
 * 
 * SECURITY:
 * - JWT authentication: Bắt buộc phải có token
 * - Role-based authorization: Chỉ ORGANIZER/STAFF
 * - EventId validation: Chống SQL injection
 * - Nên kiểm tra ORGANIZER chỉ xem được sự kiện của mình (ownership)
 * 
 * USE CASES:
 * - Dashboard ORGANIZER: Hiển thị thống kê sự kiện
 * - Báo cáo sự kiện: Xuất PDF/Excel với số liệu
 * - Theo dõi real-time: Cập nhật số lượng check-in trong sự kiện
 * - Phân tích hiệu quả: So sánh attendance rate giữa các sự kiện
 * 
 * NÂNG CẤP ĐỀ XUẤT:
 * - Thêm filter theo thời gian (startDate, endDate)
 * - Thống kê theo ticket category (VIP, Standard, Free...)
 * - Export CSV/PDF
 * - Real-time updates với WebSocket
 * - Cache thống kê (Redis) để giảm load DB
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/TicketDAO.java (getEventStats method)
 * - DTO: DTO/EventStatsDTO.java (response structure)
 * - Utils: utils/JwtUtils.java (verify token, parse claims)
 * - Filter: filter/JwtAuthFilter.java (authentication middleware)
 */

import DAO.TicketDAO;
import DTO.EventStatsDTO;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

@WebServlet("/api/events/stats")
public class EventStatsController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final Gson gson = new Gson();

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

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("STAFF"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Permission denied. ORGANIZER or STAFF only.\"}");
            return;
        }

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

        EventStatsDTO stats = ticketDAO.getEventStats(eventId);
        if (stats == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            JsonObject j = new JsonObject();
            j.addProperty("error", "Event not found or no tickets");
            resp.getWriter().write(gson.toJson(j));
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(stats));
    }
}