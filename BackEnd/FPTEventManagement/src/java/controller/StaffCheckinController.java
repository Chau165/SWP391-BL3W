package controller;

import DAO.TicketDAO;
import DAO.EventDAO;
import DTO.Ticket;
import DTO.Event;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/staff/checkin")
public class StaffCheckinController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final EventDAO eventDAO = new EventDAO();
    private final Gson gson = new Gson();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // ✅ Cho phép check-in sớm 30 phút
    private static final long EARLY_CHECKIN_MS = 30L * 60L * 1000L; // 30 minutes

    // CORS
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

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        // ===== 1. Kiểm tra Token =====
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Vui lòng đăng nhập để thực hiện check-in\"}");
            return;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại\"}");
            return;
        }

        // ===== 2. Kiểm tra quyền =====
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Bạn không có quyền thực hiện check-in\"}");
            return;
        }

        // ===== 3. Lấy mã vé từ QR =====
        String qrValue = req.getParameter("ticketCode");
        if (qrValue == null || qrValue.trim().isEmpty()) {
            qrValue = req.getParameter("ticketId");
        }

        if (qrValue == null || qrValue.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Không tìm thấy mã vé. Vui lòng quét lại mã QR\"}");
            return;
        }

        qrValue = qrValue.trim();
        System.out.println("[StaffCheckin] qrValue = " + qrValue);

        // ===== 4. Parse danh sách vé =====
        List<Integer> ticketIds = new ArrayList<>();

        try {
            if (qrValue.startsWith("TICKETS:")) {
                String idsPart = qrValue.substring("TICKETS:".length());
                String[] parts = idsPart.split(",");
                for (String p : parts) {
                    if (p != null && !p.trim().isEmpty()) {
                        ticketIds.add(Integer.parseInt(p.trim()));
                    }
                }
            } else {
                ticketIds.add(Integer.parseInt(qrValue));
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Mã QR không hợp lệ. Vui lòng quét lại\"}");
            return;
        }

        if (ticketIds.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Không có vé nào được tìm thấy từ mã QR\"}");
            return;
        }

        // ===== 5. Xử lý check-in từng vé =====
        Timestamp now = new Timestamp(System.currentTimeMillis());
        JsonArray resultArray = new JsonArray();
        int successCount = 0;
        int failCount = 0;

        for (Integer ticketId : ticketIds) {
            JsonObject item = new JsonObject();
            item.addProperty("ticketId", ticketId);

            // Kiểm tra vé tồn tại
            Ticket ticket = ticketDAO.getTicketById(ticketId);
            if (ticket == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Vé #" + ticketId + " không tồn tại trong hệ thống");
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Lấy thông tin sự kiện
            Event event = eventDAO.getEventById(ticket.getEventId());
            if (event == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Không tìm thấy thông tin sự kiện của vé #" + ticketId);
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Thêm tên sự kiện để FE hiển thị
            item.addProperty("eventName", event.getTitle());

            Timestamp eventStartTime = event.getStartTime();
            Timestamp eventEndTime = event.getEndTime();

            // ===== ✅ NEW: cho phép check-in sớm 30 phút =====
            if (eventStartTime != null) {
                long earliestCheckinMillis = eventStartTime.getTime() - EARLY_CHECKIN_MS;
                Timestamp earliestCheckinTime = new Timestamp(earliestCheckinMillis);

                // Nếu chưa tới thời điểm check-in sớm
                if (now.before(earliestCheckinTime)) {
                    String earliestStr = dateFormat.format(earliestCheckinTime);
                    String startStr = dateFormat.format(eventStartTime);

                    item.addProperty("success", false);
                    item.addProperty("message",
                            "Chưa tới thời gian check-in. Có thể check-in từ: " + earliestStr
                                    + " (Sự kiện bắt đầu lúc " + startStr + ")");
                    item.addProperty("earliestCheckinTime", earliestCheckinTime.toString());
                    item.addProperty("eventStartTime", eventStartTime.toString());
                    failCount++;
                    resultArray.add(item);
                    continue;
                }
            }

            // Kiểm tra đã quá giờ kết thúc sự kiện chưa (giữ nguyên)
            if (eventEndTime != null && now.after(eventEndTime)) {
                String endTimeStr = dateFormat.format(eventEndTime);
                item.addProperty("success", false);
                item.addProperty("message", "Sự kiện đã kết thúc lúc " + endTimeStr);
                item.addProperty("eventEndTime", eventEndTime.toString());
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Kiểm tra trạng thái vé
            String currentStatus = ticket.getStatus();
            item.addProperty("currentStatus", currentStatus);

            if ("CHECKED_IN".equalsIgnoreCase(currentStatus)) {
                String checkinTimeStr = ticket.getCheckinTime() != null
                        ? dateFormat.format(ticket.getCheckinTime())
                        : "không rõ";
                item.addProperty("success", false);
                item.addProperty("message", "Vé đã được check-in lúc " + checkinTimeStr);
                if (ticket.getCheckinTime() != null) {
                    item.addProperty("previousCheckinTime", ticket.getCheckinTime().toString());
                }
                failCount++;
                resultArray.add(item);
                continue;
            }

            if (!"BOOKED".equalsIgnoreCase(currentStatus)) {
                String statusMsg = "";
                switch (currentStatus.toUpperCase()) {
                    case "CANCELLED":
                        statusMsg = "Vé đã bị hủy";
                        break;
                    case "EXPIRED":
                        statusMsg = "Vé đã hết hạn";
                        break;
                    case "PENDING":
                        statusMsg = "Vé chưa được thanh toán";
                        break;
                    default:
                        statusMsg = "Vé có trạng thái không hợp lệ: " + currentStatus;
                }
                item.addProperty("success", false);
                item.addProperty("message", statusMsg);
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Thực hiện check-in
            boolean ok = ticketDAO.checkinTicket(ticketId, now);
            if (!ok) {
                item.addProperty("success", false);
                item.addProperty("message", "Không thể check-in vé. Vui lòng thử lại");
                failCount++;
            } else {
                item.addProperty("success", true);
                item.addProperty("status", "CHECKED_IN");
                item.addProperty("checkinTime", now.toString());
                item.addProperty("message", "Check-in thành công vé #" + ticketId);
                successCount++;
            }
            resultArray.add(item);
        }

        // ===== 6. Tạo message tổng hợp =====
        String mainMessage;
        boolean isSuccess = (failCount == 0);

        if (ticketIds.size() == 1) {
            JsonObject result = resultArray.get(0).getAsJsonObject();
            mainMessage = result.get("message").getAsString();
        } else {
            if (isSuccess) {
                mainMessage = String.format("Check-in thành công %d vé", successCount);
            } else if (successCount == 0) {
                mainMessage = String.format("Check-in thất bại cho tất cả %d vé", failCount);
            } else {
                mainMessage = String.format("Check-in thành công %d/%d vé",
                        successCount, ticketIds.size());
            }
        }

        // ===== 7. Trả về response =====
        JsonObject resJson = new JsonObject();
        resJson.addProperty("success", isSuccess);
        resJson.addProperty("message", mainMessage);
        resJson.addProperty("totalTickets", ticketIds.size());
        resJson.addProperty("successCount", successCount);
        resJson.addProperty("failCount", failCount);
        resJson.add("results", resultArray);

        resp.setStatus(isSuccess ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write(gson.toJson(resJson));
    }
}
