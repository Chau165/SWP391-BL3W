package controller;

import DAO.ReportDAO;
import DTO.ReportListStaffDTO;

import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * API: GET /api/staff/reports
 *
 * MỤC ĐÍCH:
 * - STAFF/ADMIN xem danh sách report do student gửi lên (phục vụ duyệt/ xử lý report).
 * - Có hỗ trợ filter theo status (optional) + phân trang (page, pageSize).
 *
 * INPUT (query params):
 * - status (optional): ví dụ "PENDING", "APPROVED", "REJECTED"...
 * - page (optional): trang hiện tại, mặc định 1
 * - pageSize (optional): số item mỗi trang, mặc định 10
 *
 * OUTPUT:
 * - 200 OK: {"status":"success","page":1,"pageSize":10,"data":[...]}
 *
 * ======================= STATUS CODE (QUAN TRỌNG ĐỂ TRẢ LỜI THẦY/CÔ) =======================
 *
 * 200 OK
 * - OPTIONS preflight thành công (doOptions)
 * - GET thành công trả danh sách report
 *
 * 401 UNAUTHORIZED
 * - Thiếu Authorization header hoặc không đúng format "Bearer ..."
 * - Token không hợp lệ / hết hạn
 *
 * 403 FORBIDDEN
 * - Token hợp lệ nhưng role không phải STAFF/ADMIN (không đủ quyền)
 *
 * (Không có 400/404/500 trong code hiện tại)
 * - Nếu DAO/DB lỗi runtime -> có thể bị container trả 500 mặc định (vì không try/catch ở doGet).
 */
@WebServlet("/api/staff/reports")
public class StaffReportListController extends HttpServlet {

    // DAO thao tác DB liên quan Report
    private final ReportDAO reportDAO = new ReportDAO();

    // Gson dùng để convert list DTO -> JSON
    private final Gson gson = new Gson();

    /**
     * OPTIONS: xử lý CORS preflight (browser gửi trước nếu có Authorization header)
     *
     * STATUS CODE:
     * - 200 OK
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);                 // set các header CORS cần thiết
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * GET /api/staff/reports
     *
     * FLOW:
     * (0) CORS + encoding + contentType
     * (1) AUTH: kiểm tra Bearer token
     * (2) Validate token
     * (3) Check role STAFF/ADMIN
     * (4) Đọc query params (status, page, pageSize)
     * (5) Query DB lấy list
     * (6) Trả JSON
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // (0) CORS + encoding
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8"); // đảm bảo đọc tiếng Việt ổn (nếu có params Unicode)
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // (1) Auth: lấy Authorization header dạng "Bearer <token>"
        String auth = req.getHeader("Authorization");

        // Nếu thiếu token hoặc không đúng format -> 401 Unauthorized
        // STATUS CODE: 401
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Lấy token thực tế (bỏ "Bearer ")
        String token = auth.substring(7);

        // Token invalid/hết hạn -> 401 Unauthorized
        // STATUS CODE: 401
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // (3) Lấy role và staffId từ token
        String role = JwtUtils.getRoleFromToken(token);
        Integer staffId = JwtUtils.getIdFromToken(token);

        // Chỉ cho STAFF hoặc ADMIN
        // Nếu token hợp lệ nhưng không đủ quyền -> 403 Forbidden
        // STATUS CODE: 403
        boolean allowed = staffId != null && role != null &&
                ("STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));

        if (!allowed) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Staff/Admin mới được xem danh sách report\"}");
            return;
        }

        // (4) Query params (lọc + phân trang)
        // status optional: nếu null nghĩa là không filter (lấy tất cả)
        String status = req.getParameter("status");

        // page mặc định 1 nếu thiếu / parse lỗi
        int page = parseIntOrDefault(req.getParameter("page"), 1);

        // pageSize mặc định 10 nếu thiếu / parse lỗi
        int pageSize = parseIntOrDefault(req.getParameter("pageSize"), 10);

        // (5) Query DB
        // listReportsForStaff(status, page, pageSize):
        // - status có thể lọc theo trạng thái
        // - page/pageSize để phân trang
        List<ReportListStaffDTO> list =
                (List<ReportListStaffDTO>) reportDAO.listReportsForStaff(status, page, pageSize);

        // (6) Response success
        // STATUS CODE: 200 OK
        resp.setStatus(HttpServletResponse.SC_OK); // 200

        // Trả JSON gồm metadata phân trang + data list
        out.print("{\"status\":\"success\",\"page\":" + page +
                ",\"pageSize\":" + pageSize +
                ",\"data\":" + gson.toJson(list) + "}");
    }

    /**
     * parseIntOrDefault:
     * - Nếu query param null hoặc parse lỗi -> trả giá trị mặc định (def)
     * - Tránh làm API crash khi FE truyền page="abc"
     *
     * Lưu ý: hiện tại nếu user truyền page=-1 vẫn nhận (vì không validate >0)
     * (Bạn có thể nói với thầy: code này mới xử lý parse lỗi, chưa validate range)
     */
    private int parseIntOrDefault(String s, int def) {
        try {
            if (s == null) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * setCorsHeaders:
     * - Whitelist domain FE để gọi API
     * - Cho phép Authorization header để gửi JWT
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

/**
 * ======================= CÂU HỎI THẦY/CÔ CÓ THỂ HỎI (BẠN LUYỆN TRẢ LỜI) =======================
 *
 * 1) API này dùng để làm gì?
 *    - Cho STAFF/ADMIN xem danh sách report của student để xử lý/duyệt.
 *
 * 2) Vì sao trả 401 khi thiếu token, và 403 khi sai role?
 *    - 401: chưa xác thực (chưa đăng nhập / token sai)
 *    - 403: đã xác thực nhưng không đủ quyền.
 *
 * 3) status/page/pageSize dùng để làm gì?
 *    - status: lọc theo trạng thái report (PENDING/APPROVED/REJECTED...)
 *    - page/pageSize: phân trang để không trả quá nhiều dữ liệu một lần.
 *
 * 4) parseIntOrDefault để làm gì?
 *    - Tránh lỗi NumberFormatException khi user truyền page/pageSize không phải số.
 *
 * 5) Nếu DB lỗi thì status code nào?
 *    - Code hiện tại không try/catch ở doGet, nên nếu DAO throw exception thì server/container có thể trả 500.
 *
 * 6) Vì sao doOptions trả 200?
 *    - Browser cần preflight CORS cho request có Authorization header; trả 200 để browser cho phép gọi GET thật.
 */
