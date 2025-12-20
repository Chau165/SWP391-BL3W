package controller;

// DAO: lớp thao tác DB cho bảng/đối tượng EventRequest
import DAO.EventRequestDAO;

// DTO: object đại diện cho một request yêu cầu tạo sự kiện
import DTO.EventRequest;

// Gson: parse JSON body từ FE thành object Java
import com.google.gson.Gson;

// JwtUtils: tiện ích validate token + lấy role/userId từ JWT
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;          // BufferedReader, PrintWriter...
import java.sql.Timestamp; // lưu thời gian vào DB

/**
 * Controller nhận request tạo "Event Request" (yêu cầu tạo sự kiện)
 * Endpoint: POST /api/event-requests
 *
 * Luồng tổng quát:
 * 1) CORS (Options + headers)
 * 2) Check Authorization Bearer token
 * 3) Validate token + check role ORGANIZER
 * 4) Đọc JSON body
 * 5) Validate dữ liệu (title, thời gian)
 * 6) Parse thời gian về Timestamp
 * 7) Map vào DTO EventRequest + set status PENDING
 * 8) Insert DB -> trả về requestId
 */
@WebServlet("/api/event-requests")
public class CreateEventRequestController extends HttpServlet {

    // DAO để insert request vào database
    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();

    // Gson dùng parse JSON request body
    private final Gson gson = new Gson();

    /**
     * Class nội bộ mô tả "body request" mà FE gửi lên.
     * FE sẽ gửi JSON chứa: title, description, preferredStartTime, preferredEndTime, expectedCapacity
     *
     * Ví dụ body FE:
     * {
     *   "title": "Workshop AI",
     *   "description": "Sự kiện training",
     *   "preferredStartTime": "2025-12-10T09:00",
     *   "preferredEndTime": "2025-12-10T11:00",
     *   "expectedCapacity": 100
     * }
     */
    private static class CreateReqBody {
        String title;
        String description;
        String preferredStartTime; // FE có thể gửi dạng ISO: "2025-12-10T09:00:00.134Z" hoặc "2025-12-10T09:00"
        String preferredEndTime;
        Integer expectedCapacity;
    }

    /**
     * doOptions dùng để xử lý CORS preflight.
     * Browser sẽ gọi OPTIONS trước khi POST nếu:
     * - Có header Authorization
     * - Hoặc Content-Type đặc biệt
     *
     * Mục đích: server trả header CORS cho browser biết "được phép gọi POST từ origin này".
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);             // set các header Access-Control-*
        resp.setStatus(HttpServletResponse.SC_OK); // trả 200 OK cho preflight
    }

    /**
     * doPost là API chính: tạo EventRequest.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // ====== (0) CORS + encoding/response type ======
        setCorsHeaders(resp, req); // nếu không set CORS thì browser sẽ chặn request

        req.setCharacterEncoding("UTF-8");                // đảm bảo đọc body tiếng Việt không lỗi
        resp.setContentType("application/json;charset=UTF-8"); // response dạng JSON

        PrintWriter out = resp.getWriter(); // dùng để trả JSON về FE

        // =========================================================
        // (1) AUTH: Lấy token từ Authorization header & validate role
        // =========================================================

        // FE gửi: Authorization: Bearer <token>
        String auth = req.getHeader("Authorization");

        // Nếu không có Authorization hoặc không bắt đầu bằng "Bearer " => coi như chưa login
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        // Cắt "Bearer " để lấy token thật
        String token = auth.substring(7);

        // Validate token: thường check chữ ký + hết hạn + hợp lệ format
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        // Lấy role và userId từ token (không tin userId gửi từ client)
        String role = JwtUtils.getRoleFromToken(token);
        Integer userId = JwtUtils.getIdFromToken(token);

        // Authorization: chỉ ORGANIZER mới được tạo request
        // Nếu token có nhưng role không đúng => 403 Forbidden
        if (userId == null || role == null || !"ORGANIZER".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Organizer mới được tạo request sự kiện\"}");
            return;
        }

        // =========================================================
        // (2) Đọc JSON body từ FE
        // =========================================================

        // Đọc body raw text
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        // Parse JSON -> CreateReqBody bằng Gson
        CreateReqBody body = gson.fromJson(sb.toString(), CreateReqBody.class);

        // Validate: body null hoặc title trống => 400 Bad Request
        if (body == null || isBlank(body.title)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Title không được để trống\"}");
            return;
        }

        // Validate: thiếu start/end => 400
        if (isBlank(body.preferredStartTime) || isBlank(body.preferredEndTime)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian bắt đầu / kết thúc không được để trống\"}");
            return;
        }

        // =========================================================
        // (3) Parse & validate thời gian
        // =========================================================

        // Chuyển chuỗi time từ FE thành Timestamp để lưu DB
        Timestamp startTime = parseDateTime(body.preferredStartTime);
        Timestamp endTime   = parseDateTime(body.preferredEndTime);

        // Nếu parse fail => format sai => 400
        if (startTime == null || endTime == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Định dạng thời gian không hợp lệ\"}");
            return;
        }

        // Lấy thời điểm hiện tại server
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // Rule nghiệp vụ: không cho chọn quá khứ
        if (startTime.before(now)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian bắt đầu phải ở hiện tại hoặc tương lai\"}");
            return;
        }

        // Rule nghiệp vụ: end phải sau start
        if (endTime.before(startTime)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            out.print("{\"status\":\"fail\",\"message\":\"Thời gian kết thúc phải sau thời gian bắt đầu\"}");
            return;
        }

        // =========================================================
        // (4) Map dữ liệu vào DTO EventRequest (object để insert DB)
        // =========================================================
        EventRequest er = new EventRequest();

        // requesterId lấy từ token => đảm bảo đúng user đang login
        er.setRequesterId(userId);

        // Trim để bỏ khoảng trắng thừa
        er.setTitle(body.title.trim());

        // description có thể null, nếu có thì trim
        er.setDescription(body.description != null ? body.description.trim() : null);

        // Lưu thời gian mong muốn
        er.setPreferredStartTime(startTime);
        er.setPreferredEndTime(endTime);

        // expectedCapacity có thể null, tuỳ DB cho phép
        er.setExpectedCapacity(body.expectedCapacity);

        // status mặc định: PENDING vì cần staff duyệt
        er.setStatus("PENDING");

        // =========================================================
        // (5) Insert DB
        // =========================================================

        // Insert request -> trả về id mới (auto increment/identity)
        Integer newId = eventRequestDAO.insertRequest(er);

        // Nếu insert fail (DAO trả null) => lỗi server => 500
        if (newId == null) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            out.print("{\"status\":\"error\",\"message\":\"Không tạo được request sự kiện\"}");
            return;
        }

        // =========================================================
        // (6) Trả kết quả về FE
        // =========================================================

        // 201 Created: tạo mới thành công
        resp.setStatus(HttpServletResponse.SC_CREATED);

        // Trả JSON cho FE hiển thị toast + lấy requestId
        out.print("{\"status\":\"success\",\"message\":\"Tạo request sự kiện thành công\",\"requestId\":" + newId + "}");
    }

    /**
     * parseDateTime: parse thời gian FE gửi sang Timestamp
     *
     * Hỗ trợ các kiểu:
     * - "2025-12-10T09:00:00"
     * - "2025-12-10T09:00:00.134Z" (có Z ở cuối)
     * - "2025-12-10T09:00"
     *
     * Flow:
     * 1) trim, check rỗng
     * 2) nếu có Z thì bỏ Z
     * 3) thay T thành space
     * 4) nếu thiếu giây (:ss) thì thêm ":00"
     * 5) dùng Timestamp.valueOf("yyyy-MM-dd HH:mm:ss") để parse
     */
    private Timestamp parseDateTime(String s) {
        if (s == null) return null;
        String value = s.trim();
        if (value.isEmpty()) return null;

        // Nếu FE gửi ISO UTC có Z -> bỏ Z
        if (value.endsWith("Z") || value.endsWith("z")) {
            value = value.substring(0, value.length() - 1);
        }

        // "2025-12-10T09:00" -> "2025-12-10 09:00"
        value = value.replace('T', ' ');

        // Nếu chỉ có "yyyy-MM-dd HH:mm" thì thêm ":00" để đủ "HH:mm:ss"
        if (value.length() == 16) {
            value = value + ":00";
        }

        try {
            // Timestamp.valueOf yêu cầu format "yyyy-[m]m-[d]d hh:mm:ss[.f...]"
            return Timestamp.valueOf(value);
        } catch (Exception e) {
            // Nếu parse lỗi -> log để debug
            System.err.println("[WARN] parseDateTime failed for value = " + s + " -> " + e.getMessage());
            return null;
        }
    }

    // Helper check string null/rỗng
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * setCorsHeaders:
     * - Lấy Origin từ request
     * - Nếu origin nằm trong whitelist -> cho phép gọi API từ domain đó
     * - Nếu không -> chặn (Access-Control-Allow-Origin = null)
     *
     * Các header quan trọng:
     * - Allow-Origin: domain được phép
     * - Allow-Credentials: cho phép gửi cookie/credentials
     * - Allow-Methods: phương thức cho phép
     * - Allow-Headers: cho phép header Authorization
     * - Max-Age: cache preflight 86400s
     */
    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        // Whitelist origin FE (localhost + ngrok)
        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

        if (allowed) {
            // Cho phép đúng origin gửi request
            res.setHeader("Access-Control-Allow-Origin", origin);

            // Cho phép gửi credentials nếu cần (cookie/session)
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            // Không nằm whitelist -> browser sẽ chặn
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        // Để cache/proxy phân biệt response theo Origin
        res.setHeader("Vary", "Origin");

        // Cho phép method
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        // Cho phép các header FE hay dùng (đặc biệt Authorization)
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");

        // Header nào client được phép đọc ra (nếu cần)
        res.setHeader("Access-Control-Expose-Headers", "Authorization");

        // Thời gian browser cache kết quả preflight OPTIONS (giảm số lần OPTIONS)
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
