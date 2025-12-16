package controller;

import DAO.TicketDAO;
import DAO.EventDAO;
import DTO.Ticket;
import DTO.Event;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import service.SystemConfigService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/staff/checkout")
public class StaffCheckoutController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final EventDAO eventDAO = new EventDAO();
    private final SystemConfigService systemConfigService = new SystemConfigService();

    private final Gson gson = new Gson();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    // CORS (copy y hệt checkin)
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

        // 1) Token
        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Vui lòng đăng nhập để thực hiện check-out\"}");
            return;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại\"}");
            return;
        }

        // 2) Role
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"error\":\"Bạn không có quyền thực hiện check-out\"}");
            return;
        }

        // 3) Lấy mã vé từ QR
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
        System.out.println("[StaffCheckout] qrValue = " + qrValue);

        // 4) Parse danh sách ticketIds
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

        // ✅ Load config 1 lần cho toàn request (đọc đúng file runtime)
        // YÊU CẦU: SystemConfigService đã có hàm load(ServletContext ctx) như mình gửi trước đó.
        SystemConfigService.SystemConfig cfg = systemConfigService.load(req.getServletContext());
        int minMinutesAfterStart = cfg.minMinutesAfterStart;

        // ✅ LOG ra config đang áp dụng + file đang đọc
        String realPath = req.getServletContext().getRealPath("/WEB-INF/classes/config/SystemConfig.json");
        System.out.println("[StaffCheckout] CONFIG realPath = " + realPath);
        System.out.println("[StaffCheckout] minMinutesAfterStart = " + minMinutesAfterStart);

        // (Optional) log raw json để chắc 100% đang đọc đúng file
        try {
            if (realPath != null) {
                String raw = new String(Files.readAllBytes(Paths.get(realPath)), StandardCharsets.UTF_8);
                System.out.println("[StaffCheckout] CONFIG raw json = " + raw);
            }
        } catch (Exception e) {
            System.out.println("[StaffCheckout] Read config raw failed: " + e.getMessage());
        }

        // 5) Xử lý check-out từng vé
        Timestamp now = new Timestamp(System.currentTimeMillis());
        JsonArray resultArray = new JsonArray();
        int successCount = 0;
        int failCount = 0;

        for (Integer ticketId : ticketIds) {
            JsonObject item = new JsonObject();
            item.addProperty("ticketId", ticketId);

            Ticket ticket = ticketDAO.getTicketById(ticketId);
            if (ticket == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Vé #" + ticketId + " không tồn tại trong hệ thống");
                failCount++;
                resultArray.add(item);
                continue;
            }

            Event event = eventDAO.getEventById(ticket.getEventId());
            if (event == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Không tìm thấy thông tin sự kiện của vé #" + ticketId);
                failCount++;
                resultArray.add(item);
                continue;
            }

            item.addProperty("eventName", event.getTitle());

            // ===== ✅ RULE: chỉ cho check-out sau X phút kể từ lúc event bắt đầu =====
            Timestamp eventStartTime = event.getStartTime();
            Timestamp eventEndTime = event.getEndTime();

            if (eventStartTime == null || eventEndTime == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Sự kiện không có thời gian hợp lệ để check-out");
                failCount++;
                resultArray.add(item);
                continue;
            }

            long allowCheckoutMs = eventStartTime.getTime() + (minMinutesAfterStart * 60L * 1000L);
            Timestamp allowCheckoutTime = new Timestamp(allowCheckoutMs);

            if (now.before(allowCheckoutTime)) {
                String allowStr = dateFormat.format(allowCheckoutTime);
                item.addProperty("success", false);
                item.addProperty("message",
                        "Chưa đủ thời gian để check-out. Có thể check-out từ: " + allowStr
                                + " (cấu hình: " + minMinutesAfterStart + " phút sau khi bắt đầu)");
                item.addProperty("allowCheckoutTime", allowCheckoutTime.toString());
                item.addProperty("minMinutesAfterStart", minMinutesAfterStart);
                failCount++;
                resultArray.add(item);
                continue;
            }
            // ===== END RULE =====

            String currentStatus = ticket.getStatus();
            item.addProperty("currentStatus", currentStatus);

            if ("CHECKED_OUT".equalsIgnoreCase(currentStatus)) {
                item.addProperty("success", false);
                item.addProperty("message", "Vé đã được check-out trước đó");
                failCount++;
                resultArray.add(item);
                continue;
            }

            if (!"CHECKED_IN".equalsIgnoreCase(currentStatus)) {
                item.addProperty("success", false);
                item.addProperty("message",
                        "Không thể check-out vì vé chưa check-in hoặc trạng thái không hợp lệ: " + currentStatus);
                failCount++;
                resultArray.add(item);
                continue;
            }

            boolean ok = ticketDAO.checkoutTicket(ticketId);
            if (!ok) {
                item.addProperty("success", false);
                item.addProperty("message", "Không thể check-out vé. Vui lòng thử lại");
                failCount++;
            } else {
                item.addProperty("success", true);
                item.addProperty("status", "CHECKED_OUT");
                item.addProperty("checkoutTime", now.toString());
                item.addProperty("message", "Check-out thành công vé #" + ticketId);
                successCount++;
            }

            resultArray.add(item);
        }

        // 6) Message tổng hợp
        boolean isSuccess = (failCount == 0);
        String mainMessage;

        if (ticketIds.size() == 1) {
            JsonObject result = resultArray.get(0).getAsJsonObject();
            mainMessage = result.get("message").getAsString();
        } else {
            if (isSuccess) {
                mainMessage = String.format("Check-out thành công %d vé", successCount);
            } else if (successCount == 0) {
                mainMessage = String.format("Check-out thất bại cho tất cả %d vé", failCount);
            } else {
                mainMessage = String.format("Check-out thành công %d/%d vé", successCount, ticketIds.size());
            }
        }

        // 7) Response
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
