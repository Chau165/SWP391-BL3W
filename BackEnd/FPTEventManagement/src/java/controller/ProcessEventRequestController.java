package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import utils.JwtUtils;
import utils.InMemoryNotificationService;   // ✅ THÊM IMPORT

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.Timestamp;

@WebServlet("/api/event-requests/process")
public class ProcessEventRequestController extends HttpServlet {

    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();
    private final Gson gson = new Gson();

    // Body từ FE
    private static class ProcessBody {
        Integer requestId;
        String action;        // "APPROVE" hoặc "REJECT"
        String organizerNote; // optional
        Integer areaId;       // required nếu APPROVE
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

        // 1) Check JWT & role
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

        if (userId == null || role == null ||
                !( "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role) )) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ STAFF hoặc ADMIN được duyệt request\"}");
            return;
        }

        // 2) Đọc body JSON
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        ProcessBody body = gson.fromJson(sb.toString(), ProcessBody.class);

        if (body == null || body.requestId == null || isBlank(body.action)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu requestId / action\"}");
            return;
        }

        String action = body.action.trim().toUpperCase();

        // 3) Lấy Event_Request theo id
        EventRequest reqObj = eventRequestDAO.getById(body.requestId);
        if (reqObj == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"Không tìm thấy request\"}");
            return;
        }

        if (!"PENDING".equalsIgnoreCase(reqObj.getStatus())) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ xử lý được request đang ở trạng thái PENDING\"}");
            return;
        }

        // 4) Switch action
        switch (action) {
            case "APPROVE":
                handleApprove(reqObj, body, userId, resp, out);
                break;
            case "REJECT":
                handleReject(reqObj, body, userId, resp, out);
                break;
            default:
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"status\":\"fail\",\"message\":\"Action không hợp lệ. Hãy dùng APPROVE hoặc REJECT\"}");
        }
    }

    // ======================== HANDLE APPROVE ========================
    private void handleApprove(EventRequest reqObj,
                               ProcessBody body,
                               int staffId,
                               HttpServletResponse resp,
                               PrintWriter out) throws IOException {

        // Bắt buộc có areaId
        if (body.areaId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu areaId khi APPROVE\"}");
            return;
        }

        // Phải có thời gian trong request
        Timestamp start = reqObj.getPreferredStartTime();
        Timestamp end   = reqObj.getPreferredEndTime();
        if (start == null || end == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Request không có thời gian bắt đầu/kết thúc hợp lệ\"}");
            return;
        }

        // Check trùng lịch ở DAO
        boolean conflict = eventRequestDAO.hasAreaConflict(body.areaId, start, end);
        if (conflict) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            out.print("{\"status\":\"fail\",\"message\":\"Khu vực đã có sự kiện khác trong khoảng thời gian này\"}");
            return;
        }

        // APPROVE + TẠO EVENT (transaction trong DAO)
        Integer newEventId = eventRequestDAO.approveRequestAndCreateEvent(
                reqObj,
                staffId,
                body.areaId,
                body.organizerNote
        );

        if (newEventId == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể tạo Event hoặc cập nhật request\"}");
            return;
        }

        // ✅ TẠO NOTIFICATION CHO ORGANIZER (người gửi request)
        Integer requesterId = reqObj.getRequesterId();  // giả sử EventRequest có field này
        if (requesterId != null) {
            String title = "Yêu cầu sự kiện đã được duyệt";
            String content = "Yêu cầu tổ chức sự kiện \"" + reqObj.getTitle() + "\" của bạn đã được duyệt.";
            // Link FE: tùy bạn map, có thể dẫn tới chi tiết event mới tạo
            String linkUrl = "/events/" + newEventId; // hoặc "/organizer/event-requests/" + reqObj.getRequestId()

            InMemoryNotificationService.addNotification(requesterId, title, content, linkUrl);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đã APPROVE request và tạo Event thành công\",\"eventId\":" + newEventId + "}");
    }

    // ======================== HANDLE REJECT ========================
    private void handleReject(EventRequest reqObj,
                              ProcessBody body,
                              int staffId,
                              HttpServletResponse resp,
                              PrintWriter out) {

        boolean ok = eventRequestDAO.rejectRequest(reqObj.getRequestId(), staffId, body.organizerNote);

        if (!ok) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật trạng thái REJECTED\"}");
            return;
        }

        // ✅ TẠO NOTIFICATION CHO ORGANIZER KHI BỊ TỪ CHỐI
        Integer requesterId = reqObj.getRequesterId();
        if (requesterId != null) {
            String title = "Yêu cầu sự kiện bị từ chối";
            String content = "Yêu cầu tổ chức sự kiện \"" + reqObj.getTitle() + "\" của bạn đã bị từ chối.";
            String linkUrl = "/organizer/event-requests/" + reqObj.getRequestId();

            InMemoryNotificationService.addNotification(requesterId, title, content, linkUrl);
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        out.print("{\"status\":\"success\",\"message\":\"Đã REJECT request thành công\"}");
    }

    // ======================== HELPER ========================
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
