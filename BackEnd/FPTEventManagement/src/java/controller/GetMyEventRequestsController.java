package controller;

import DAO.EventRequestDAO;          // DAO: truy vấn bảng EventRequest trong DB
import DTO.EventRequest;             // DTO: object mapping dữ liệu request sự kiện
import com.google.gson.Gson;         // Gson: convert Java object -> JSON
import utils.JwtUtils;               // JwtUtils: validate token + lấy role/userId từ token

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;

/**
 * API: GET /api/event-requests/my
 *
 * MỤC ĐÍCH:
 * - Trả về danh sách request tạo sự kiện của CHÍNH organizer đang đăng nhập.
 *
 * AI GỌI:
 * - Frontend (màn hình "My Event Requests" / "Lịch sử request" của Organizer).
 *
 * STATUS CODE TỔNG QUAN:
 * - 200 OK: thành công (trả JSON list)
 * - 401 Unauthorized: thiếu token hoặc token không hợp lệ
 * - 403 Forbidden: token đúng nhưng không đúng role (không phải ORGANIZER)
 *
 * CÂU HỎI THẦY/CÔ HAY HỎI:
 * - "Vì sao lấy userId từ token mà không nhận userId từ client?"
 * - "Vì sao chỉ ORGANIZER xem được?"
 * - "Thiếu token thì trả 401 hay 403? khác nhau sao?"
 * - "Vì sao có doOptions (CORS preflight)?"
 */
@WebServlet("/api/event-requests/my")
public class GetMyEventRequestsController extends HttpServlet {

    // DAO để lấy danh sách request theo userId
    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();

    // Gson để trả list ra JSON
    private final Gson gson = new Gson();

    /**
     * doOptions:
     * - Dùng cho CORS preflight khi FE gọi API có header Authorization
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Tại sao phải có OPTIONS?"
     *   => Vì browser sẽ gửi OPTIONS trước khi gửi GET nếu request có header đặc biệt (Authorization).
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);

        // ✅ STATUS CODE: 200 OK (OPTIONS preflight OK)
        resp.setStatus(HttpServletResponse.SC_OK); // = 200
    }

    /**
     * doGet:
     * FLOW:
     * 1) Set CORS + content-type
     * 2) Check Authorization header (Bearer token)
     * 3) Validate JWT
     * 4) Lấy role + userId từ token
     * 5) Check role ORGANIZER
     * 6) Query DB lấy list request theo userId
     * 7) Convert list -> JSON và trả về
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ===================== 1) AUTH: kiểm tra token =====================
        String auth = req.getHeader("Authorization");

        /**
         * Nếu thiếu Authorization hoặc không đúng format "Bearer ..."
         *
         * ✅ STATUS CODE: 401 Unauthorized
         * - Vì đây là lỗi "chưa đăng nhập / chưa chứng thực"
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Vì sao thiếu token là 401 chứ không phải 403?"
         *   => 401 = chưa xác thực, 403 = có xác thực nhưng không đủ quyền.
         */
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // = 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt chuỗi "Bearer " (7 ký tự) để lấy token thật
        String token = auth.substring(7);

        /**
         * Nếu token không hợp lệ (sai chữ ký/ hết hạn/ bị sửa)
         *
         * ✅ STATUS CODE: 401 Unauthorized
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Token hết hạn thì sao?" => validateToken false -> 401
         */
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // = 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // ===================== 2) AUTHZ: kiểm tra quyền (role) =====================
        // Lấy role và userId từ token (đảm bảo userId không bị giả mạo từ client)
        String role = JwtUtils.getRoleFromToken(token);
        Integer userId = JwtUtils.getIdFromToken(token);

        /**
         * Nếu không lấy được userId/role hoặc role không phải ORGANIZER
         *
         * ✅ STATUS CODE: 403 Forbidden
         * - Vì user đã "xác thực thành công" (token valid) nhưng "không đủ quyền"
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Tại sao chỉ ORGANIZER được gọi?"
         *   => Vì đây là API xem request do Organizer tạo (màn hình My Requests).
         * - "Nếu STAFF gọi thì sao?" => 403
         * - "Vì sao không cho client gửi userId?"
         *   => Nếu client gửi userId thì có thể xem request của người khác (lỗ hổng bảo mật).
         */
        if (userId == null || role == null || !"ORGANIZER".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // = 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ ORGANIZER mới được xem request của mình\"}");
            return;
        }

        // ===================== 3) BUSINESS: lấy dữ liệu của chính user =====================
        /**
         * Lấy list request của organizer theo userId.
         *
         * Lưu ý: tên hàm getRequestsByStudent(userId) có thể đặt tên chưa chuẩn
         * nhưng ý nghĩa là: lấy theo requesterId (organizer tạo request).
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Tại sao query theo userId ở DB mà không lấy hết rồi filter?"
         *   => Query DB theo userId sẽ nhanh hơn, ít data trả về, đúng bảo mật.
         * - "DAO join processedByName để làm gì?"
         *   => FE hiển thị ai đã duyệt request (Staff), tránh FE phải gọi thêm API khác.
         */
        List<EventRequest> list = eventRequestDAO.getRequestsByStudent(userId);

        /**
         * Trả list ra JSON
         *
         * ✅ STATUS CODE: mặc định vẫn là 200 OK (nếu không set thì container vẫn trả 200)
         * - Nếu list rỗng vẫn 200 (không phải lỗi) -> FE hiển thị "chưa có request"
         *
         * CÂU HỎI THẦY/CÔ:
         * - "Nếu organizer chưa có request nào?" => trả [] và 200 OK
         * - "Sao không wrap {status, data}?" => đơn giản, FE parse list trực tiếp
         */
        String json = gson.toJson(list);
        out.print(json);
    }

    /**
     * setCorsHeaders:
     * - Cho phép FE gọi API từ localhost/ngrok
     * - Cho phép header Authorization (bắt buộc vì dùng JWT)
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Bỏ CORS thì FE bị gì?" => bị browser chặn (CORS policy)
     * - "Vì sao cần OPTIONS?" => preflight khi có Authorization header
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

        // Allow GET vì API này chỉ GET, OPTIONS để preflight, POST giữ sẵn theo project
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        // Cho phép FE gửi Authorization để gửi JWT
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");

        // FE có thể đọc Authorization header nếu backend trả header mới (refresh token...)
        res.setHeader("Access-Control-Expose-Headers", "Authorization");

        // Cache preflight 24h
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
