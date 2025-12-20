package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;  // ✅ dùng GsonBuilder để cấu hình serialize

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import utils.JwtUtils;

/**
 * API: GET /api/staff/event-requests
 *
 * MỤC ĐÍCH:
 * - STAFF/ADMIN xem danh sách các yêu cầu tổ chức sự kiện (EventRequest).
 * - Backend lấy danh sách request từ DB rồi phân loại ra 3 nhóm:
 *   + pending   : đang chờ duyệt
 *   + approved  : đã duyệt
 *   + rejected  : đã từ chối
 * - Trả về JSON dạng:
 *   {
 *     "pending":  [...],
 *     "approved": [...],
 *     "rejected": [...]
 *   }
 *
 * ======================= STATUS CODE TRẢ VỀ =======================
 * 200 OK:
 *   - OPTIONS preflight thành công
 *   - GET thành công và trả JSON result (mặc định nếu không setStatus khác)
 *
 * 401 UNAUTHORIZED:
 *   - Thiếu Authorization header hoặc không đúng format "Bearer ..."
 *   - Token không hợp lệ (JwtUtils.validateToken false)
 *
 * 403 FORBIDDEN:
 *   - Token hợp lệ nhưng role không phải STAFF/ADMIN
 *
 * (Lưu ý)
 * - Code hiện tại KHÔNG bắt exception -> nếu DAO lỗi runtime có thể trả 500 Internal Server Error (mặc định container)
 *   => nếu thầy hỏi, bạn có thể nói: "nên thêm try/catch để trả 500 + message rõ ràng".
 */
@WebServlet("/api/staff/event-requests")
public class GetPendingEventRequestsController extends HttpServlet {

    // DAO thao tác DB với bảng EventRequest (lấy danh sách requests)
    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();

    /**
     * Gson cấu hình serializeNulls:
     * - Dù field null (vd: processedByName, organizerNote...) vẫn xuất hiện trong JSON
     * - FE dễ render/kiểm tra hơn (khỏi đoán field có tồn tại hay không)
     *
     * CÂU HỎI THẦY CÔ HAY HỎI:
     * - Vì sao cần serializeNulls? => để FE nhận đủ schema (field null vẫn có), tránh FE bị undefined.
     */
    private final Gson gson = new GsonBuilder()
            .serializeNulls()   // ✅ giữ cả field null
            .create();

    /**
     * OPTIONS: CORS preflight
     * STATUS: 200 OK
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao cần OPTIONS? => browser gửi preflight khi gọi API có Authorization header.
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * GET: endpoint chính
     *
     * FLOW:
     * 1) Set CORS + content-type
     * 2) Check token (401 nếu thiếu/sai)
     * 3) Check role STAFF/ADMIN (403 nếu không đủ quyền)
     * 4) Lấy list request từ DB
     * 5) Chia thành 3 list theo status
     * 6) Trả JSON {pending, approved, rejected}
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ===================== 1) AUTH + ROLE =====================

        // Lấy Authorization header: "Bearer <token>"
        String auth = req.getHeader("Authorization");

        // Nếu thiếu header hoặc sai prefix => 401 Unauthorized
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt token khỏi chuỗi "Bearer "
        String token = auth.substring(7);

        // Token không hợp lệ => 401 Unauthorized
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role từ token để phân quyền
        String role = JwtUtils.getRoleFromToken(token);

        // Nếu role không phải STAFF hoặc ADMIN => 403 Forbidden
        if (role == null || (!"STAFF".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ STAFF/ADMIN mới xem được danh sách request\"}");
            return;
        }

        // ===================== 2) LOAD DATA TỪ DB =====================

        /**
         * Lấy danh sách request từ DB.
         * Chú ý: tên hàm getPendingRequests() nhưng comment nói lấy cả PENDING/APPROVED/REJECTED.
         * => Nếu thầy cô hỏi:
         *   "Sao hàm tên getPendingRequests mà trả cả 3 trạng thái?"
         *   Bạn trả lời:
         *   - DAO thực tế đang trả tất cả request (hoặc join thêm info) và mình phân loại tại controller.
         *   - Nên đổi tên hàm cho đúng (vd getAllRequestsForStaff) để code rõ hơn.
         */
        List<EventRequest> all = eventRequestDAO.getPendingRequests();

        // Tạo 3 list để phân loại theo status
        List<EventRequest> pending = new ArrayList<>();
        List<EventRequest> approved = new ArrayList<>();
        List<EventRequest> rejected = new ArrayList<>();

        // Duyệt từng request và phân nhóm theo status
        for (EventRequest r : all) {
            if ("PENDING".equalsIgnoreCase(r.getStatus())) {
                pending.add(r);
            } else if ("APPROVED".equalsIgnoreCase(r.getStatus())) {
                approved.add(r);
            } else if ("REJECTED".equalsIgnoreCase(r.getStatus())) {
                rejected.add(r);
            }
            // Nếu có status khác (VD: CANCELLED) thì hiện tại sẽ bị bỏ qua
            // CÂU HỎI THẦY CÔ:
            // - Nếu có status mới thì sao? => nên bổ sung else thêm nhóm "others" hoặc update logic.
        }

        // ===================== 3) BUILD RESPONSE JSON =====================

        // Đóng gói kết quả thành object/map để FE nhận 3 mảng riêng
        Map<String, Object> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("rejected", rejected);

        // Convert sang JSON và trả về client
        // STATUS mặc định là 200 OK nếu không set khác
        String json = gson.toJson(result);
        out.print(json);
    }

    /**
     * setCorsHeaders:
     * - Cho phép FE từ localhost / ngrok gọi API
     * - Nếu origin không nằm whitelist -> Access-Control-Allow-Origin = null
     *
     * CÂU HỎI THẦY CÔ:
     * - Vì sao set Vary: Origin? => cache theo origin khác nhau.
     * - Vì sao allow credentials? => nếu dùng cookie/session vẫn hoạt động (dù ở đây dùng Bearer token).
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
