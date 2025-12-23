package controller;

import DAO.ReportDAO;
import DTO.Report;

import com.google.gson.Gson;

import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.sql.Timestamp;

@WebServlet("/api/student/reports")
public class StudentCreateReportController extends HttpServlet {

    // DAO thao tác DB cho Report (insert report, check ticket ownership, check pending report...)
    private final ReportDAO reportDAO = new ReportDAO();

    // Gson dùng để parse JSON body từ FE và có thể dùng để toJson nếu cần
    private final Gson gson = new Gson();

    /**
     * Body FE gửi lên (JSON) để tạo report.
     * Ví dụ JSON:
     * {
     *   "ticketId": 123,
     *   "title": "Báo cáo sự cố",
     *   "description": "Mô tả chi tiết...",
     *   "imageUrl": "http://..."
     * }
     */
    private static class CreateReportBody {
        Integer ticketId;
        String title;
        String description;
        String imageUrl;
    }

    /**
     * OPTIONS /api/student/reports
     * - Browser preflight CORS khi FE gọi POST có Authorization header.
     *
     * STATUS CODE:
     * - 200 OK: preflight thành công
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * POST /api/student/reports
     *
     * FLOW TỔNG:
     * (0) CORS + Encoding
     * (1) Auth: Bearer token + validate token + check role STUDENT
     * (2) Đọc JSON body
     * (3) Validate input + validate ticket ownership + check duplicate pending report
     * (4) Map sang DTO Report + insert DB
     * (5) Trả response (201 Created)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // (0) CORS + encoding
        setCorsHeaders(resp, req);                 // cho phép FE domain hợp lệ gọi API
        req.setCharacterEncoding("UTF-8");         // để đọc tiếng Việt từ request body đúng
        resp.setContentType("application/json;charset=UTF-8"); // response trả JSON UTF-8

        PrintWriter out = resp.getWriter(); // writer để ghi JSON response

        // =========================================================
        // (1) AUTH: Bearer token + validate role STUDENT
        // =========================================================
        // Lấy header Authorization (dạng: "Bearer <jwt_token>")
        String auth = req.getHeader("Authorization");

        // Nếu không có token hoặc sai format -> 401 Unauthorized
        // STATUS CODE: 401
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt bỏ "Bearer " để lấy token thật
        String token = auth.substring(7);

        // Validate token (hết hạn, sai signature...) -> 401 Unauthorized
        // STATUS CODE: 401
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role và userId từ token
        String role = JwtUtils.getRoleFromToken(token);
        Integer studentId = JwtUtils.getIdFromToken(token);

        // Nếu không phải STUDENT -> 403 Forbidden
        // STATUS CODE: 403
        if (studentId == null || role == null || !"STUDENT".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Student mới được gửi report\"}");
            return;
        }

        // =========================================================
        // (2) Đọc JSON body
        // =========================================================
        // Đọc raw body thành 1 chuỗi JSON (StringBuilder)
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        // Parse JSON -> object CreateReportBody
        CreateReportBody body = gson.fromJson(sb.toString(), CreateReportBody.class);

        // =========================================================
        // (3) Validate input
        // =========================================================

        // ticketId phải tồn tại và > 0 -> nếu sai => 400 Bad Request
        // STATUS CODE: 400
        if (body == null || body.ticketId == null || body.ticketId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"ticketId không hợp lệ\"}");
            return;
        }

        // description bắt buộc -> nếu trống => 400 Bad Request
        // STATUS CODE: 400
        if (isBlank(body.description)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"description không được để trống\"}");
            return;
        }

        // Ticket phải thuộc student đang login
        // Nếu ticket không phải của user => 403 Forbidden (cấm truy cập tài nguyên của người khác)
        // STATUS CODE: 403
        if (!reportDAO.isTicketOwnedByUser(body.ticketId, studentId)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Ticket không thuộc về bạn\"}");
            return;
        }

        // Tránh trùng report pending: nếu ticket này đã có report trạng thái pending -> 409 Conflict
        // STATUS CODE: 409
        if (reportDAO.hasPendingReportForTicket(body.ticketId)) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
            out.print("{\"status\":\"fail\",\"message\":\"Ticket này đã có report đang chờ duyệt\"}");
            return;
        }

        // =========================================================
        // (4) Map DTO + insert DB
        // =========================================================
        // Tạo Report DTO để insert vào DB
        Report r = new Report();
        r.setUserId(studentId); // user gửi report (student)
        r.setTicketId(body.ticketId); // report gắn với ticket cụ thể
        r.setTitle(body.title != null ? body.title.trim() : null); // title optional
        r.setDescription(body.description.trim()); // description bắt buộc
        r.setImageUrl(body.imageUrl != null ? body.imageUrl.trim() : null); // ảnh optional

        // Insert DB, trả về id mới
        int newId = reportDAO.insertReport(r);

        // Nếu insert fail -> 500 Internal Server Error
        // STATUS CODE: 500
        if (newId <= 0) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            out.print("{\"status\":\"error\",\"message\":\"Không tạo được report\"}");
            return;
        }

        // =========================================================
        // (5) Response
        // =========================================================
        // Tạo report thành công -> 201 Created
        // STATUS CODE: 201
        resp.setStatus(HttpServletResponse.SC_CREATED); // 201
        out.print("{\"status\":\"success\",\"message\":\"Gửi report thành công\",\"reportId\":" + newId + "}");
    }

    // Helper: check null/empty
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * setCorsHeaders:
     * - Cho phép FE (localhost/ngrok) gọi API.
     * - Allow-Headers có Authorization để gửi JWT.
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
 * ======================= TỔNG HỢP STATUS CODE (HỌC THUỘC NHANH) =======================
 *
 * 200 OK
 * - OPTIONS preflight (doOptions)
 *
 * 201 CREATED
 * - Tạo report thành công (insert DB ok) -> trả reportId
 *
 * 400 BAD REQUEST
 * - ticketId null/<=0
 * - description rỗng
 *
 * 401 UNAUTHORIZED
 * - Thiếu Authorization header / không có Bearer token
 * - Token không hợp lệ (hết hạn / sai)
 *
 * 403 FORBIDDEN
 * - Không phải role STUDENT
 * - Ticket không thuộc về student đang đăng nhập
 *
 * 409 CONFLICT
 * - Ticket đã có report trạng thái PENDING (tránh gửi trùng)
 *
 * 500 INTERNAL SERVER ERROR
 * - Insert report thất bại (newId <= 0)
 *
 *
 * ======================= CÂU HỎI THẦY/CÔ HAY HỎI (BẠN LUYỆN TRẢ LỜI) =======================
 *
 * 1) Vì sao thiếu token lại trả 401?
 *    - Vì client chưa “xác thực” (chưa chứng minh đăng nhập) nên server trả Unauthorized.
 *
 * 2) Vì sao không phải STUDENT lại trả 403?
 *    - Vì đã có token hợp lệ nhưng role không có quyền tạo report => Forbidden.
 *
 * 3) Vì sao ticket không thuộc user lại trả 403?
 *    - Đây là kiểm soát truy cập tài nguyên (authorization): không được thao tác ticket của người khác.
 *
 * 4) Vì sao “đã có report pending” dùng 409 Conflict?
 *    - Vì request hợp lệ nhưng xung đột trạng thái tài nguyên: ticket đó đang có report chờ duyệt.
 *
 * 5) Vì sao tạo thành công trả 201 thay vì 200?
 *    - Theo REST: tạo mới resource (report) => 201 Created là đúng chuẩn.
 *
 * 6) Body JSON đọc kiểu StringBuilder + fromJson có nhược điểm gì?
 *    - Dài dòng; có thể parse trực tiếp: gson.fromJson(req.getReader(), CreateReportBody.class).
 *
 * 7) Nếu muốn bắt buộc title thì phải sửa ở đâu?
 *    - Thêm validate: isBlank(body.title) -> trả 400.
 */
