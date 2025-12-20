package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import utils.JwtUtils;
import utils.InMemoryNotificationService;   // ✅ Service lưu notification tạm trong RAM (không lưu DB)

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.Timestamp;

/**
 * API: POST /api/event-requests/process
 *
 * MỤC ĐÍCH:
 * - STAFF/ADMIN duyệt (APPROVE) hoặc từ chối (REJECT) 1 EventRequest.
 * - Nếu APPROVE:
 *   + validate thời gian + areaId
 *   + check trùng lịch (CONFLICT)
 *   + approve request + tạo Event mới (transaction trong DAO)
 *   + tạo notification cho ORGANIZER (người gửi request)
 * - Nếu REJECT:
 *   + update status REJECTED + note
 *   + tạo notification cho ORGANIZER
 *
 * ======================= STATUS CODE TRẢ VỀ =======================
 * 200 OK:
 *   - OPTIONS preflight
 *   - APPROVE thành công
 *   - REJECT thành công
 *
 * 400 BAD REQUEST:
 *   - thiếu requestId / action trong body
 *   - action không hợp lệ (không phải APPROVE/REJECT)
 *   - request không ở trạng thái PENDING
 *   - APPROVE nhưng thiếu areaId
 *   - request thiếu start/end time hợp lệ
 *
 * 401 UNAUTHORIZED:
 *   - thiếu Authorization header (Bearer ...)
 *   - token không hợp lệ
 *
 * 403 FORBIDDEN:
 *   - user không phải STAFF/ADMIN (không đủ quyền)
 *
 * 404 NOT FOUND:
 *   - không tìm thấy requestId trong DB
 *
 * 409 CONFLICT:
 *   - APPROVE nhưng khu vực (area) bị trùng lịch trong khoảng start-end
 *
 * 500 INTERNAL SERVER ERROR:
 *   - approveRequestAndCreateEvent lỗi / trả null (không tạo event hoặc không update request được)
 *   - rejectRequest update DB thất bại
 */
@WebServlet("/api/event-requests/process")
public class ProcessEventRequestController extends HttpServlet {

    // DAO thao tác bảng EventRequest (+ các query check conflict + transaction approve tạo event)
    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();

    // Gson dùng để parse JSON body và trả JSON response
    private final Gson gson = new Gson();

    /**
     * DTO nhỏ để map JSON body từ FE gửi lên.
     * FE thường gửi:
     * {
     *   "requestId": 1,
     *   "action": "APPROVE" | "REJECT",
     *   "organizerNote": "...",
     *   "areaId": 10
     * }
     *
     * CÂU HỎI THẦY CÔ HAY HỎI:
     * - Tại sao cần class ProcessBody? => để parse JSON body sang object rõ ràng, tránh đọc từng field thủ công.
     * - areaId vì sao chỉ required khi APPROVE? => vì duyệt thì phải gán event vào 1 area cụ thể.
     */
    private static class ProcessBody {
        Integer requestId;
        String action;        // "APPROVE" hoặc "REJECT"
        String organizerNote; // ghi chú của staff/admin (optional)
        Integer areaId;       // required nếu APPROVE
    }

    /**
     * OPTIONS: xử lý CORS preflight.
     * STATUS: 200 OK
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao cần OPTIONS? => Browser sẽ gửi preflight khi gọi POST có Authorization header.
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * POST: endpoint chính để process request.
     * Flow tổng:
     * 1) CORS + charset
     * 2) Check JWT + role
     * 3) Đọc body JSON -> ProcessBody
     * 4) Lấy request theo id + validate status PENDING
     * 5) Switch action:
     *    - APPROVE -> handleApprove
     *    - REJECT  -> handleReject
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");                 // cho phép body note tiếng Việt
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ======================= (1) AUTH: Check JWT & role =======================
        // Lấy Authorization header: "Bearer <token>"
        String auth = req.getHeader("Authorization");

        // Nếu thiếu token hoặc sai format -> 401 Unauthorized
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt token thật ra khỏi "Bearer "
        String token = auth.substring(7);

        // Token không hợp lệ -> 401 Unauthorized
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role và userId từ token để phân quyền + lưu người duyệt
        String role = JwtUtils.getRoleFromToken(token);
        Integer userId = JwtUtils.getIdFromToken(token);

        // Nếu không phải STAFF/ADMIN -> 403 Forbidden
        // (câu message hiện ghi "STAFF hoặc ADMIN", nhưng trong code check đúng STAFF/ADMIN)
        if (userId == null || role == null ||
                !( "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role) )) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ STAFF hoặc ADMIN được duyệt request\"}");
            return;
        }

        // ======================= (2) READ BODY JSON =======================
        // Đọc toàn bộ body JSON thành chuỗi
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        // Parse JSON -> object ProcessBody
        ProcessBody body = gson.fromJson(sb.toString(), ProcessBody.class);

        // Validate body tối thiểu: requestId + action
        // Thiếu -> 400 Bad Request
        if (body == null || body.requestId == null || isBlank(body.action)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu requestId / action\"}");
            return;
        }

        // Chuẩn hoá action về dạng in hoa
        String action = body.action.trim().toUpperCase();

        // ======================= (3) LOAD REQUEST THEO ID =======================
        // Lấy request từ DB
        EventRequest reqObj = eventRequestDAO.getById(body.requestId);

        // Không có request -> 404 Not Found
        if (reqObj == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
            out.print("{\"status\":\"fail\",\"message\":\"Không tìm thấy request\"}");
            return;
        }

        // Chỉ cho xử lý request đang PENDING
        // Nếu request đã APPROVED/REJECTED rồi -> 400 Bad Request
        if (!"PENDING".equalsIgnoreCase(reqObj.getStatus())) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ xử lý được request đang ở trạng thái PENDING\"}");
            return;
        }

        // ======================= (4) SWITCH ACTION =======================
        // CÂU HỎI THẦY CÔ:
        // - Vì sao cần switch? => tách 2 flow nghiệp vụ rõ ràng: APPROVE khác REJECT.
        switch (action) {
            case "APPROVE":
                // APPROVE: check area/time/conflict -> tạo event + update request
                handleApprove(reqObj, body, userId, resp, out);
                break;

            case "REJECT":
                // REJECT: update status + note
                handleReject(reqObj, body, userId, resp, out);
                break;

            default:
                // Action lạ -> 400
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
                out.print("{\"status\":\"fail\",\"message\":\"Action không hợp lệ. Hãy dùng APPROVE hoặc REJECT\"}");
        }
    }

    // ======================== HANDLE APPROVE ========================
    /**
     * handleApprove:
     * - Validate areaId (bắt buộc)
     * - Validate start/end time trong request
     * - Check conflict lịch area (409 nếu trùng)
     * - Gọi DAO approveRequestAndCreateEvent(...) (transaction)
     * - Tạo notification cho organizer
     *
     * STATUS:
     * - 200 OK: duyệt + tạo event thành công
     * - 400: thiếu areaId / thời gian request không hợp lệ
     * - 409: conflict lịch
     * - 500: lỗi tạo event / update DB
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao conflict trả 409? => đúng semantics REST: tài nguyên xung đột trạng thái (trùng lịch).
     * - Vì sao tạo Event trong DAO transaction? => đảm bảo update request + insert event đi chung, tránh lệch dữ liệu.
     */
    private void handleApprove(EventRequest reqObj,
                               ProcessBody body,
                               int staffId,
                               HttpServletResponse resp,
                               PrintWriter out) throws IOException {

        // areaId bắt buộc khi APPROVE
        if (body.areaId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu areaId khi APPROVE\"}");
            return;
        }

        // Lấy thời gian từ request (organizer đề xuất)
        Timestamp start = reqObj.getPreferredStartTime();
        Timestamp end   = reqObj.getPreferredEndTime();

        // Nếu request thiếu time -> 400 (dữ liệu request sai/thiếu)
        if (start == null || end == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Request không có thời gian bắt đầu/kết thúc hợp lệ\"}");
            return;
        }

        // Check trùng lịch của area trong khoảng start-end
        boolean conflict = eventRequestDAO.hasAreaConflict(body.areaId, start, end);

        // Nếu trùng -> 409 Conflict
        if (conflict) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
            out.print("{\"status\":\"fail\",\"message\":\"Khu vực đã có sự kiện khác trong khoảng thời gian này\"}");
            return;
        }

        // Duyệt request và tạo event mới (transaction trong DAO)
        Integer newEventId = eventRequestDAO.approveRequestAndCreateEvent(
                reqObj,
                staffId,
                body.areaId,
                body.organizerNote
        );

        // Nếu tạo event/update request fail -> 500
        if (newEventId == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            out.print("{\"status\":\"error\",\"message\":\"Không thể tạo Event hoặc cập nhật request\"}");
            return;
        }

        // ======================= NOTIFICATION =======================
        /**
         * Tạo notification cho organizer (người gửi request).
         * Hiện tại dùng InMemoryNotificationService => chỉ sống trong RAM
         *
         * CÂU HỎI THẦY CÔ:
         * - InMemory có nhược điểm gì? => restart server là mất notification; multi-server không đồng bộ.
         * - Vì sao vẫn dùng? => demo nhanh, chưa triển khai DB/queue; sau có thể thay bằng Notification table.
         */
        Integer requesterId = reqObj.getRequesterId();  // organizer
        if (requesterId != null) {
            String title = "Yêu cầu sự kiện đã được duyệt";
            String content = "Yêu cầu tổ chức sự kiện \"" + reqObj.getTitle() + "\" của bạn đã được duyệt.";
            String linkUrl = "/events/" + newEventId; // FE có thể dẫn sang trang event mới

            InMemoryNotificationService.addNotification(requesterId, title, content, linkUrl);
        }

        // Thành công -> 200 OK
        resp.setStatus(HttpServletResponse.SC_OK); // 200
        out.print("{\"status\":\"success\",\"message\":\"Đã APPROVE request và tạo Event thành công\",\"eventId\":" + newEventId + "}");
    }

    // ======================== HANDLE REJECT ========================
    /**
     * handleReject:
     * - Update DB: set REJECTED + processedBy + organizerNote
     * - Tạo notification cho organizer
     *
     * STATUS:
     * - 200 OK: reject thành công
     * - 500: update DB thất bại
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao reject vẫn trả 200? => thao tác xử lý request thành công (không phải “lỗi hệ thống”).
     */
    private void handleReject(EventRequest reqObj,
                              ProcessBody body,
                              int staffId,
                              HttpServletResponse resp,
                              PrintWriter out) {

        // DAO update trạng thái REJECTED
        boolean ok = eventRequestDAO.rejectRequest(reqObj.getRequestId(), staffId, body.organizerNote);

        // Nếu update fail -> 500
        if (!ok) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            out.print("{\"status\":\"error\",\"message\":\"Không thể cập nhật trạng thái REJECTED\"}");
            return;
        }

        // Tạo notification cho organizer khi bị từ chối
        Integer requesterId = reqObj.getRequesterId();
        if (requesterId != null) {
            String title = "Yêu cầu sự kiện bị từ chối";
            String content = "Yêu cầu tổ chức sự kiện \"" + reqObj.getTitle() + "\" của bạn đã bị từ chối.";
            String linkUrl = "/organizer/event-requests/" + reqObj.getRequestId();

            InMemoryNotificationService.addNotification(requesterId, title, content, linkUrl);
        }

        // Thành công -> 200 OK
        resp.setStatus(HttpServletResponse.SC_OK); // 200
        out.print("{\"status\":\"success\",\"message\":\"Đã REJECT request thành công\"}");
    }

    // ======================== HELPER ========================
    /**
     * isBlank: check string null hoặc rỗng sau trim
     * Dùng để validate action.
     */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * setCorsHeaders:
     * - Cho FE gọi API từ localhost / ngrok
     * - Bật credentials để gửi cookie nếu cần
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao set Vary: Origin? => để cache/proxy phân biệt response theo từng Origin.
     * - Vì sao whitelist domain? => tránh mở CORS toàn bộ (bảo mật).
     */
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
