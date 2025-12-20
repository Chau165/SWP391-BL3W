package controller;

import DAO.VenueAreaDAO;          // DAO: truy vấn DB liên quan VenueArea
import DTO.VenueArea;             // DTO: object mapping khu vực (area)
import com.google.gson.Gson;      // Gson: convert Java object -> JSON
import utils.JwtUtils;            // JwtUtils: validate token + lấy role từ token

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API: GET /api/areas/free?startTime=...&endTime=...
 *
 * MỤC ĐÍCH:
 * - Trả về danh sách khu vực (VenueArea) đang TRỐNG trong khoảng thời gian [startTime, endTime]
 * - Có "buffer 1 giờ" để tránh trùng lịch sát giờ (setup/dọn dẹp, chuẩn bị sân khấu...)
 *
 * AI GỌI:
 * - Frontend: màn hình tạo event / chọn khu vực tổ chức (thường Organizer/Staff/Admin dùng)
 *
 * STATUS CODE TỔNG QUAN:
 * - 200 OK: thành công, trả {status:"success", areas:[...]}
 * - 401 Unauthorized: thiếu token hoặc token không hợp lệ
 * - 403 Forbidden: token đúng nhưng không đúng role được phép
 * - 400 Bad Request: thiếu/sai startTime endTime, hoặc start/end không hợp lệ
 *
 * CÂU HỎI THẦY/CÔ HAY HỎI:
 * - "Tại sao cần buffer 1 giờ?"
 * - "Tại sao phải validate startTime/endTime ở backend?"
 * - "Thiếu token là 401 hay 403? khác nhau sao?"
 * - "Vì sao role nào được gọi? ORGANIZER hay STAFF/ADMIN?"
 */
@WebServlet("/api/areas/free")
public class GetFreeAreasController extends HttpServlet {

    // DAO để query danh sách area trống
    private final VenueAreaDAO venueAreaDAO = new VenueAreaDAO();

    // Gson để trả JSON
    private final Gson gson = new Gson();

    /**
     * doOptions: xử lý preflight CORS (browser gọi trước GET)
     *
     * ✅ STATUS CODE: 200 OK
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Vì sao có OPTIONS?"
     *   => Vì FE gửi header Authorization nên browser preflight trước.
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK); // = 200
    }

    /**
     * doGet: FE gọi để lấy area trống
     *
     * FLOW CHẠY:
     * 1) Check JWT + role
     * 2) Nhận startTime/endTime từ query param
     * 3) Parse và validate thời gian
     * 4) Query DB lấy area trống theo start-end + buffer 1h
     * 5) Build JSON response trả về FE
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ===================== 1) Check JWT & role =====================
        /**
         * CÂU HỎI THẦY/CÔ:
         * - "API này có public không?" => Không, vì liên quan lịch & đặt khu vực -> say role.
         * - "Thiếu token trả gì?" => 401.
         */
        String auth = req.getHeader("Authorization");

        // Thiếu Authorization hoặc không đúng format Bearer
        if (auth == null || !auth.startsWith("Bearer ")) {
            // ✅ STATUS CODE: 401 Unauthorized
            // Vì chưa xác thực (chưa đăng nhập / thiếu token)
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // = 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Lấy token thật từ "Bearer <token>"
        String token = auth.substring(7);

        // Token sai/hết hạn/bị sửa
        if (!JwtUtils.validateToken(token)) {
            // ✅ STATUS CODE: 401 Unauthorized
            // Vì token không hợp lệ => xác thực thất bại
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // = 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role trong token
        String role = JwtUtils.getRoleFromToken(token);

        /**
         * NOTE: Code bạn check role STAFF hoặc ADMIN
         *
         * CÂU HỎI THẦY/CÔ hay xoáy:
         * - "Trong comment ghi ORGANIZER/ADMIN, nhưng code lại STAFF/ADMIN, sao vậy?"
         *   => Bạn trả lời theo thiết kế thật của bạn:
         *      + Nếu hệ thống cho STAFF/ADMIN quản lý khu vực -> đúng như code.
         *      + Nếu muốn ORGANIZER cũng được xem -> cần chỉnh condition (nhưng hiện tại code đang restrict).
         */
        if (role == null ||
                !( "STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role) )) {
            // ✅ STATUS CODE: 403 Forbidden
            // Vì token hợp lệ nhưng không có quyền
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // = 403

            // NOTE: message đang ghi "ORGANIZER hoặc ADMIN" nhưng if lại STAFF/ADMIN
            // -> nếu thầy hỏi, bạn nói: "em set quyền người quản lý khu vực là STAFF/ADMIN"
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ ORGANIZER hoặc ADMIN mới được xem area trống\"}");
            return;
        }

        // ===================== 2) Lấy query param startTime & endTime =====================
        /**
         * FE gọi dạng:
         * /api/areas/free?startTime=2025-12-10T09:00&endTime=2025-12-10T11:00
         */
        String startStr = req.getParameter("startTime");
        String endStr   = req.getParameter("endTime");

        // Thiếu startTime hoặc endTime
        if (isBlank(startStr) || isBlank(endStr)) {
            // ✅ STATUS CODE: 400 Bad Request
            // Vì client gửi thiếu dữ liệu bắt buộc
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu startTime hoặc endTime\"}");
            return;
        }

        // ===================== 3) Parse & validate time =====================
        Timestamp start = parseDateTime(startStr);
        Timestamp end   = parseDateTime(endStr);

        // Sai format thời gian
        if (start == null || end == null) {
            // ✅ STATUS CODE: 400 Bad Request
            // Vì input format sai
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
            out.print("{\"status\":\"fail\",\"message\":\"Định dạng thời gian không hợp lệ\"}");
            return;
        }

        // endTime phải sau startTime
        if (!end.after(start)) {
            // ✅ STATUS CODE: 400 Bad Request
            // Vì logic thời gian không hợp lệ
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
            out.print("{\"status\":\"fail\",\"message\":\"endTime phải sau startTime\"}");
            return;
        }

        // Không cho chọn quá khứ (optional)
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (start.before(now)) {
            // ✅ STATUS CODE: 400 Bad Request
            // Vì không cho tạo event ở quá khứ
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
            out.print("{\"status\":\"fail\",\"message\":\"startTime phải là hiện tại hoặc tương lai\"}");
            return;
        }

        // ===================== 4) Query DB: lấy area trống với buffer 1h =====================
        /**
         * Ý nghĩa buffer 1h:
         * - Nếu event A kết thúc 10:00, event B bắt đầu 10:00 ngay lập tức thì không kịp setup/dọn dẹp.
         * - Buffer giúp tránh xung đột lịch thực tế.
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Buffer 1h đặt ở backend hay FE?" => Backend để bảo đảm business rule không bị bypass.
         * - "Tại sao không lấy theo Event_Seat_Layout hay bảng khác?" => vì đây là lịch khu vực (VenueArea).
         */
        List<VenueArea> areas = venueAreaDAO.getFreeAreasWith1hBuffer(start, end);

        // ===================== 5) Build response JSON =====================
        /**
         * Trả response dạng object:
         * {
         *   status: "success",
         *   startTime: "...",
         *   endTime: "...",
         *   bufferHours: 1,
         *   total: n,
         *   areas: [...]
         * }
         *
         * ✅ STATUS CODE: mặc định là 200 OK (nếu không set)
         * - Nếu muốn rõ ràng hơn có thể setStatus(200) nhưng hiện tại không bắt buộc.
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Nếu không có area trống?" => areas=[] total=0 vẫn 200 OK (không phải lỗi)
         */
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("startTime", start.toString());
        result.put("endTime", end.toString());
        result.put("bufferHours", 1);
        result.put("total", areas.size());
        result.put("areas", areas);

        // ✅ STATUS CODE: 200 OK (ngầm định)
        out.print(gson.toJson(result));
    }

    // ====== Helper parse datetime (giống controller khác) ======
    /**
     * parseDateTime:
     * - Hỗ trợ format FE gửi lên:
     *   + "2025-12-10T09:00"
     *   + "2025-12-10T09:00:00.134Z" (có Z)
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Vì sao bỏ Z?" => để parse về local time theo server (nhưng nếu production nên xử lý timezone chuẩn hơn)
     */
    private Timestamp parseDateTime(String s) {
        if (s == null) return null;
        String value = s.trim();
        if (value.isEmpty()) return null;

        // Bỏ 'Z' nếu có
        if (value.endsWith("Z") || value.endsWith("z")) {
            value = value.substring(0, value.length() - 1);
        }

        // Thay 'T' thành space cho hợp Timestamp.valueOf
        value = value.replace('T', ' ');

        // Nếu chỉ yyyy-MM-dd HH:mm -> thêm :00
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

    // Helper check rỗng
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * CORS:
     * - Cho phép FE gọi API từ localhost/ngrok
     * - Cho phép gửi Authorization header (JWT)
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Nếu bỏ doOptions/CORS thì FE bị gì?" => browser chặn CORS
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
