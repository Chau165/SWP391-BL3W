package controller;

import DAO.ReportDAO;
import DTO.ReportDetailStaffDTO;

import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * API: GET /api/staff/reports/detail?reportId=...
 *
 * MỤC ĐÍCH:
 * - STAFF/ADMIN xem chi tiết 1 report (reportId) do student gửi lên.
 * - Dùng cho màn hình “Report Detail” ở phía staff để xem nội dung, ảnh, ticket/event liên quan...
 *
 * ======================= STATUS CODE TRẢ VỀ (CẦN NHỚ) =======================
 *
 * 200 OK
 * - OPTIONS preflight thành công (doOptions)
 * - GET thành công, tìm thấy reportId và trả về data
 *
 * 401 UNAUTHORIZED
 * - Thiếu Authorization header hoặc không đúng format "Bearer ..."
 * - Token không hợp lệ / hết hạn
 *
 * 403 FORBIDDEN
 * - Token hợp lệ nhưng role không phải STAFF/ADMIN (không đủ quyền)
 *
 * 400 BAD REQUEST
 * - reportId param thiếu hoặc parse không được sang số (reportId không hợp lệ)
 *
 * 404 NOT FOUND
 * - reportId hợp lệ nhưng không tìm thấy report trong DB
 *
 * (Không có try/catch DB tổng ở doGet)
 * - Nếu DAO/DB lỗi runtime bất ngờ => server có thể trả 500 mặc định.
 */
@WebServlet("/api/staff/reports/detail")
public class StaffReportDetailController extends HttpServlet {

    // DAO dùng để truy vấn report trong DB
    private final ReportDAO reportDAO = new ReportDAO();

    // Gson dùng để convert DTO -> JSON
    private final Gson gson = new Gson();

    /**
     * OPTIONS: CORS preflight.
     * Browser sẽ gọi OPTIONS trước khi gọi GET thật (do request có Authorization header).
     *
     * STATUS CODE:
     * - 200 OK
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);                 // set header CORS
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * GET /api/staff/reports/detail?reportId=123
     *
     * FLOW:
     * (0) CORS + encoding
     * (1) AUTH: check Bearer token + validate token
     * (2) AUTHZ: check role STAFF/ADMIN
     * (3) Read + validate param reportId
     * (4) Query DB lấy chi tiết report
     * (5) Trả JSON success
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // (0) CORS + encoding
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // =========================
        // (1) AUTH: kiểm tra token
        // =========================
        String auth = req.getHeader("Authorization");

        // Nếu thiếu token hoặc không đúng "Bearer ..." -> 401
        // STATUS CODE: 401 UNAUTHORIZED
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // cắt "Bearer " để lấy token
        String token = auth.substring(7);

        // Token sai/hết hạn -> 401
        // STATUS CODE: 401 UNAUTHORIZED
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role + id từ token
        String role = JwtUtils.getRoleFromToken(token);
        Integer staffId = JwtUtils.getIdFromToken(token);

        // =========================
        // (2) AUTHZ: check quyền
        // =========================
        // Chỉ STAFF hoặc ADMIN mới được xem chi tiết report
        boolean allowed = staffId != null && role != null &&
                ("STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));

        // Nếu không đủ quyền -> 403
        // STATUS CODE: 403 FORBIDDEN
        if (!allowed) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Staff/Admin mới được xem report\"}");
            return;
        }

        // =========================
        // (3) Read + validate param
        // =========================
        // reportId nằm trên query string: ?reportId=...
        String reportIdStr = req.getParameter("reportId");
        int reportId;

        // Nếu reportId null hoặc không parse được int -> 400
        // STATUS CODE: 400 BAD REQUEST
        try {
            reportId = Integer.parseInt(reportIdStr);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"reportId không hợp lệ\"}");
            return;
        }

        // =========================
        // (4) Query DB
        // =========================
        // DAO trả về DTO chứa đầy đủ thông tin để staff xem report
        ReportDetailStaffDTO dto = reportDAO.getReportDetailForStaff(reportId);

        // Không tìm thấy report -> 404
        // STATUS CODE: 404 NOT FOUND
        if (dto == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
            out.print("{\"status\":\"fail\",\"message\":\"Không tìm thấy report\"}");
            return;
        }

        // =========================
        // (5) Response success
        // =========================
        // STATUS CODE: 200 OK
        resp.setStatus(HttpServletResponse.SC_OK); // 200

        // Gson serialize object -> JSON
        out.print("{\"status\":\"success\",\"data\":" + gson.toJson(dto) + "}");
    }

    /**
     * setCorsHeaders:
     * - Cho phép FE gọi từ localhost/ngrok
     * - Cho phép header Authorization để gửi JWT
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
 * 1) API này làm gì?
 *    - Trả chi tiết 1 report theo reportId cho staff/admin xem.
 *
 * 2) Vì sao cần token (Authorization: Bearer ...)?
 *    - Vì đây là dữ liệu nội bộ (report), chỉ staff/admin được xem, phải xác thực.
 *
 * 3) Khi nào trả 401 và khi nào trả 403?
 *    - 401: chưa xác thực (thiếu token / token sai / hết hạn)
 *    - 403: đã xác thực nhưng không có quyền (không phải STAFF/ADMIN)
 *
 * 4) Nếu reportId không parse được thì trả gì?
 *    - 400 Bad Request vì input sai.
 *
 * 5) Nếu reportId đúng nhưng không có dữ liệu trong DB?
 *    - 404 Not Found.
 *
 * 6) Tại sao doOptions cần trả 200?
 *    - Browser cần preflight CORS cho request có header Authorization.
 *
 * 7) Gson dùng để làm gì?
 *    - Convert DTO (ReportDetailStaffDTO) sang JSON để trả cho FE.
 */
