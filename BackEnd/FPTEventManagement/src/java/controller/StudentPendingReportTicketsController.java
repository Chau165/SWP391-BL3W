package controller;

import DAO.ReportDAO;
import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@WebServlet("/api/student/reports/pending-ticket-ids")
public class StudentPendingReportTicketsController extends HttpServlet {

    // DAO thao tác DB liên quan Report
    // Mục tiêu: lấy danh sách ticketId mà student đã gửi report và report đang ở trạng thái PENDING
    private final ReportDAO reportDAO = new ReportDAO();

    // Gson dùng để convert List<Integer> -> JSON array
    private final Gson gson = new Gson();

    /**
     * OPTIONS: dùng cho CORS preflight (trình duyệt sẽ gọi trước khi gọi GET nếu có Authorization header)
     *
     * STATUS CODE:
     * - 200 OK: preflight thành công
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);                 // set header CORS cho browser cho phép gọi
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * GET /api/student/reports/pending-ticket-ids
     *
     * MỤC ĐÍCH API:
     * - Student gọi API này để lấy danh sách ticketId đang có report trạng thái PENDING.
     * - FE dùng danh sách này để:
     *   + disable nút "Gửi report" cho ticket đã gửi và đang chờ duyệt
     *   + hoặc hiện badge "Đang chờ duyệt"
     *
     * FLOW:
     * (0) CORS + encoding + contentType
     * (1) AUTH: Bearer token
     * (2) Validate token
     * (3) Check role STUDENT + lấy studentId
     * (4) Query DB: lấy list ticketId pending
     * (5) Trả JSON success
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // (0) CORS + encoding
        setCorsHeaders(resp, req);                 // cho phép FE domain hợp lệ gọi API
        req.setCharacterEncoding("UTF-8");         // đọc tiếng Việt đúng nếu cần
        resp.setContentType("application/json;charset=UTF-8"); // trả JSON UTF-8

        PrintWriter out = resp.getWriter();        // ghi response JSON

        // (1) AUTH: lấy Authorization header dạng "Bearer <token>"
        String auth = req.getHeader("Authorization");

        // Nếu thiếu token hoặc không đúng format Bearer -> 401 Unauthorized
        // STATUS CODE: 401
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt token thật ra (bỏ chữ "Bearer ")
        String token = auth.substring(7);

        // (2) Validate token: hết hạn / sai chữ ký / token rác -> 401 Unauthorized
        // STATUS CODE: 401
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // (3) Lấy role và studentId từ token
        String role = JwtUtils.getRoleFromToken(token);
        Integer studentId = JwtUtils.getIdFromToken(token);

        // Nếu không phải STUDENT -> 403 Forbidden (có token nhưng không đủ quyền)
        // STATUS CODE: 403
        if (studentId == null || role == null || !"STUDENT".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Student mới được xem\"}");
            return;
        }

        // (4) Query DB: lấy danh sách ticketId của student mà report đang PENDING
        // Ví dụ trả về: [12, 15, 20]
        List<Integer> ids = reportDAO.getPendingTicketIdsByStudent(studentId);

        // (5) Trả response thành công
        // STATUS CODE: 200 OK
        resp.setStatus(HttpServletResponse.SC_OK); // 200

        // Trả format: {"status":"success","data":[1,2,3]}
        out.print("{\"status\":\"success\",\"data\":" + gson.toJson(ids) + "}");
    }

    /**
     * setCorsHeaders:
     * - Whitelist domain FE (localhost, ngrok) để tránh lỗi CORS.
     * - Cho phép Authorization header để gửi JWT.
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
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}

/**
 * ======================= TỔNG HỢP STATUS CODE (NHỚ ĐỂ TRẢ LỜI THẦY/CÔ) =======================
 *
 * 200 OK
 * - OPTIONS preflight thành công (doOptions)
 * - GET trả về danh sách pending ticketIds thành công
 *
 * 401 UNAUTHORIZED
 * - Thiếu Authorization header / không có Bearer token
 * - Token không hợp lệ (hết hạn / sai / bị sửa)
 *
 * 403 FORBIDDEN
 * - Token hợp lệ nhưng role không phải STUDENT hoặc studentId null
 *
 * (Không có 400/404/500 trong code hiện tại)
 * - Nếu muốn chắc hơn, có thể bắt try/catch khi DAO lỗi để trả 500 (nhưng bạn dặn không sửa code nên chỉ note)
 *
 *
 * ======================= CÂU HỎI THẦY/CÔ CÓ THỂ HỎI (BẠN LUYỆN TRẢ LỜI) =======================
 *
 * 1) API này dùng để làm gì?
 *    - Lấy danh sách ticketId mà student đã gửi report và report đang PENDING để FE chặn gửi trùng.
 *
 * 2) Vì sao phải check role STUDENT?
 *    - Vì dữ liệu là “của student”, nếu user khác gọi sẽ lộ thông tin / sai nghiệp vụ.
 *
 * 3) Vì sao thiếu token trả 401, còn sai role trả 403?
 *    - 401: chưa xác thực (không chứng minh đăng nhập)
 *    - 403: đã xác thực nhưng không có quyền truy cập.
 *
 * 4) Nếu ReportDAO lỗi DB thì sao?
 *    - Code hiện tại không catch exception -> servlet có thể ném lỗi và container trả 500 mặc định.
 *      Chuẩn hơn là bọc try/catch và trả 500 JSON.
 *
 * 5) Response format {"status":"success","data":[...]} có lợi gì?
 *    - FE dễ check status và dùng data trực tiếp, thống nhất format với các API khác.
 */
