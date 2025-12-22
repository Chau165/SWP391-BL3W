package controller;

import DAO.TicketDAO;
import DAO.EventDAO;
import DTO.Ticket;
import DTO.Event;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import service.SystemConfigService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/staff/checkout")
public class StaffCheckoutController extends HttpServlet {

    // DAO thao tác Ticket: lấy vé theo id, update trạng thái CHECKED_OUT
    private final TicketDAO ticketDAO = new TicketDAO();

    // DAO thao tác Event: lấy thông tin event để kiểm tra thời gian + show eventName
    private final EventDAO eventDAO = new EventDAO();

    // Service đọc config hệ thống (ví dụ: minMinutesAfterStart để cho phép checkout sau X phút)
    private final SystemConfigService systemConfigService = new SystemConfigService();

    // Gson để build JSON response
    private final Gson gson = new Gson();

    // Format thời gian hiển thị ra message cho dễ đọc
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    /**
     * ======================= STATUS CODE TRẢ VỀ =======================
     *
     * ✅ OPTIONS: - 200 OK: browser gọi preflight CORS
     *
     * ✅ POST (API chính): - 200 OK: + Khi xử lý thành công TẤT CẢ vé trong QR
     * (failCount == 0)
     *
     * - 400 BAD_REQUEST: + Không có ticketCode/ticketId (thiếu param) + QR
     * không parse được ticketId (NumberFormatException) + ticketIds rỗng sau
     * khi parse + Với QR nhiều vé: chỉ cần có 1 vé fail => cuối cùng
     * isSuccess=false => trả 400 (vì bạn set: resp.setStatus(isSuccess ? 200 :
     * 400))
     *
     * - 401 UNAUTHORIZED: + Thiếu token (Authorization header) + Token không
     * hợp lệ / hết hạn
     *
     * - 403 FORBIDDEN: + Role không phải ORGANIZER hoặc ADMIN
     *
     * ❗ LƯU Ý QUAN TRỌNG (để trả lời thầy/cô): - Trong vòng lặp, có nhiều lỗi
     * thuộc dạng "NOT FOUND" (ticket/event null), nhưng bạn KHÔNG trả 404 ở mức
     * HTTP, mà đưa lỗi vào results[]. HTTP cuối cùng vẫn phụ thuộc isSuccess
     * (200 hoặc 400). - Không có 500 trong code hiện tại vì không catch lỗi
     * DB/Runtime ngoài loop. Nếu TicketDAO/EventDAO ném Exception => servlet có
     * thể crash hoặc container trả 500 mặc định.
     */
    // ===================== CORS (copy y hệt checkin) =====================
    /**
     * setCorsHeaders: - Cho phép FE từ localhost/ngrok gọi API checkout (tránh
     * CORS blocked) - Nếu origin nằm whitelist -> set
     * Access-Control-Allow-Origin = origin - Nếu không -> set "null"
     *
     * CÂU HỎI THẦY/CÔ: 1) "Tại sao phải có OPTIONS?" => Browser sẽ preflight
     * khi gọi POST kèm Authorization header. 2) "Vì sao set Vary: Origin?" =>
     * để cache/proxy phân biệt response theo Origin.
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

    /**
     * doOptions: - Preflight request cho CORS. STATUS: - 200 OK
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK); // 200
    }

    /**
     * ===================== doPost: API CHECK-OUT =====================
     *
     * TRANG FE gọi: - POST /api/staff/checkout?ticketCode=... (hoặc
     * ticketId=...)
     *
     * FLOW TỔNG: 1) Check token (401 nếu thiếu/hỏng) 2) Check role (403 nếu
     * không đúng) 3) Lấy qrValue (ticketCode hoặc ticketId) (400 nếu thiếu) 4)
     * Parse ra list ticketIds (1 vé hoặc nhiều vé dạng "TICKETS:1,2,3") (400
     * nếu QR sai) 5) Load config minMinutesAfterStart từ file (rule cho phép
     * checkout sau X phút) 6) Loop từng ticket: - ticket tồn tại? event tồn
     * tại? - check rule thời gian (chưa đủ thời gian -> fail) - check status
     * hợp lệ (phải CHECKED_IN, chưa CHECKED_OUT) - update checkout => OK/fail
     * 7) Build response JSON: { success, message, totalTickets, successCount,
     * failCount, results[] } 8) Set HTTP status cuối: - success=true -> 200 -
     * success=false -> 400
     *
     * CÂU HỎI THẦY/CÔ: - "Tại sao trả 400 khi có 1 vé fail trong nhiều vé?" =>
     * vì bạn định nghĩa success là "tất cả phải OK". (Có thể cải tiến: luôn 200
     * và xem successCount/failCount; hoặc dùng 207 Multi-Status.)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        // ===================== 1) Token =====================
        // Lấy header Authorization dạng: "Bearer <token>"
        String authHeader = req.getHeader("Authorization");

        // Nếu thiếu/không đúng format Bearer => 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            resp.getWriter().write("{\"error\":\"Vui lòng đăng nhập để thực hiện check-out\"}");
            return;
        }

        // Cắt token ra khỏi "Bearer "
        String token = authHeader.substring(7);

        // Validate token => nếu fail => 401
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
            resp.getWriter().write("{\"error\":\"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại\"}");
            return;
        }

        // ===================== 2) Role =====================
        // Lấy role từ token (ADMIN / ORGANIZER / ...)
        String role = JwtUtils.getRoleFromToken(token);

        // Chỉ ORGANIZER hoặc ADMIN được checkout => nếu không => 403
        if (role == null || !(role.equalsIgnoreCase("ORGANIZER") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
            resp.getWriter().write("{\"error\":\"Bạn không có quyền thực hiện check-out\"}");
            return;
        }

        // ===================== 3) Lấy mã vé từ QR =====================
        // FE đang gọi: /api/staff/checkout?ticketCode=<ticketId>
        // Nếu FE gửi ticketId thì bạn fallback.
        String qrValue = req.getParameter("ticketCode");
        if (qrValue == null || qrValue.trim().isEmpty()) {
            qrValue = req.getParameter("ticketId");
        }

        // Nếu vẫn null/rỗng => 400
        if (qrValue == null || qrValue.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"Không tìm thấy mã vé. Vui lòng quét lại mã QR\"}");
            return;
        }

        // Trim để loại khoảng trắng
        qrValue = qrValue.trim();
        System.out.println("[StaffCheckout] qrValue = " + qrValue);

        // ===================== 4) Parse danh sách ticketIds =====================
        /**
         * QR có thể là: - "123" => 1 vé - "TICKETS:123,124,125" => nhiều vé gộp
         * chung
         */
        List<Integer> ticketIds = new ArrayList<>();
        try {
            if (qrValue.startsWith("TICKETS:")) {
                // Cắt phần sau "TICKETS:" rồi split theo dấu phẩy
                String idsPart = qrValue.substring("TICKETS:".length());
                String[] parts = idsPart.split(",");
                for (String p : parts) {
                    if (p != null && !p.trim().isEmpty()) {
                        ticketIds.add(Integer.parseInt(p.trim()));
                    }
                }
            } else {
                // QR chỉ có 1 id
                ticketIds.add(Integer.parseInt(qrValue));
            }
        } catch (NumberFormatException e) {
            // Nếu không parse được => QR sai => 400
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"Mã QR không hợp lệ. Vui lòng quét lại\"}");
            return;
        }

        // Nếu parse xong mà rỗng => 400
        if (ticketIds.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            resp.getWriter().write("{\"error\":\"Không có vé nào được tìm thấy từ mã QR\"}");
            return;
        }

        // ===================== 4.5) Load config runtime =====================
        /**
         * Load config 1 lần cho request: - cfg.minMinutesAfterStart: số phút
         * tối thiểu sau startTime mới được checkout
         *
         * CÂU HỎI THẦY/CÔ: 1) "Tại sao phải đọc config runtime từ file?" => để
         * admin cấu hình rule mà không sửa code. 2) "Đọc file ở đâu?" => trong
         * WEB-INF/classes/config/SystemConfig.json qua ServletContext realPath.
         */
        SystemConfigService.SystemConfig cfg = systemConfigService.load(req.getServletContext());
        int minMinutesAfterStart = cfg.minMinutesAfterStart;

        // Log để debug xem đang đọc đúng file nào và giá trị bao nhiêu
        String realPath = req.getServletContext().getRealPath("/WEB-INF/classes/config/SystemConfig.json");
        System.out.println("[StaffCheckout] CONFIG realPath = " + realPath);
        System.out.println("[StaffCheckout] minMinutesAfterStart = " + minMinutesAfterStart);

        // Optional debug: log raw json
        try {
            if (realPath != null) {
                String raw = new String(Files.readAllBytes(Paths.get(realPath)), StandardCharsets.UTF_8);
                System.out.println("[StaffCheckout] CONFIG raw json = " + raw);
            }
        } catch (Exception e) {
            System.out.println("[StaffCheckout] Read config raw failed: " + e.getMessage());
        }

        // ===================== 5) Xử lý check-out từng vé =====================
        Timestamp now = new Timestamp(System.currentTimeMillis()); // thời gian checkout hiện tại

        JsonArray resultArray = new JsonArray(); // mảng results[] trả về FE
        int successCount = 0;                    // đếm vé checkout thành công
        int failCount = 0;                       // đếm vé checkout thất bại

        // Loop từng ticketId để xử lý độc lập
        for (Integer ticketId : ticketIds) {
            JsonObject item = new JsonObject();            // object kết quả cho từng vé
            item.addProperty("ticketId", ticketId);

            // 5.1) Lấy ticket
            Ticket ticket = ticketDAO.getTicketById(ticketId);

            // Nếu ticket không tồn tại => fail (đưa vào results, không return)
            // (HTTP cuối sẽ quyết định theo failCount)
            if (ticket == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Vé #" + ticketId + " không tồn tại trong hệ thống");
                failCount++;
                resultArray.add(item);
                continue;
            }

            // 5.2) Lấy event theo ticket.eventId để check thời gian và show tên event
            Event event = eventDAO.getEventById(ticket.getEventId());
            if (event == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Không tìm thấy thông tin sự kiện của vé #" + ticketId);
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Trả thêm eventName để FE hiển thị
            item.addProperty("eventName", event.getTitle());

            // ===== RULE THỜI GIAN: checkout chỉ trong khung cho phép =====
            Timestamp eventStartTime = event.getStartTime();
            Timestamp eventEndTime = event.getEndTime();

// Nếu event thiếu start/end => không thể áp rule => fail
            if (eventStartTime == null || eventEndTime == null) {
                item.addProperty("success", false);
                item.addProperty("message", "Sự kiện không có thời gian hợp lệ để check-out");
                failCount++;
                resultArray.add(item);
                continue;
            }

// (1) Thời điểm cho phép checkout bắt đầu = startTime + X phút
            long allowCheckoutMs = eventStartTime.getTime() + (minMinutesAfterStart * 60L * 1000L);
            Timestamp allowCheckoutTime = new Timestamp(allowCheckoutMs);

// (2) Thời điểm kết thúc checkout = endTime + 30 phút
            int checkoutGraceMinutes = 30;
            long checkoutDeadlineMs = eventEndTime.getTime() + (checkoutGraceMinutes * 60L * 1000L);
            Timestamp checkoutDeadlineTime = new Timestamp(checkoutDeadlineMs);

// Nếu hiện tại chưa tới thời điểm cho phép => fail
            if (now.before(allowCheckoutTime)) {
                String allowStr = dateFormat.format(allowCheckoutTime);
                item.addProperty("success", false);
                item.addProperty("message",
                        "Chưa đủ thời gian để check-out. Có thể check-out từ: " + allowStr
                        + " (cấu hình: " + minMinutesAfterStart + " phút sau khi bắt đầu)");
                item.addProperty("allowCheckoutTime", allowCheckoutTime.toString());
                item.addProperty("minMinutesAfterStart", minMinutesAfterStart);
                failCount++;
                resultArray.add(item);
                continue;
            }

// ✅ Nếu đã quá hạn checkout (end + 30 phút) => fail
            if (now.after(checkoutDeadlineTime)) {
                String deadlineStr = dateFormat.format(checkoutDeadlineTime);
                item.addProperty("success", false);
                item.addProperty("message",
                        "Đã quá thời gian check-out. Hạn check-out đến: " + deadlineStr
                        + " (Sự kiện kết thúc + " + checkoutGraceMinutes + " phút)");
                item.addProperty("checkoutDeadlineTime", checkoutDeadlineTime.toString());
                item.addProperty("checkoutGraceMinutes", checkoutGraceMinutes);
                item.addProperty("eventEndTime", eventEndTime.toString());
                failCount++;
                resultArray.add(item);
                continue;
            }
// ===== END RULE THỜI GIAN =====

            // 5.3) Check trạng thái vé
            String currentStatus = ticket.getStatus();
            item.addProperty("currentStatus", currentStatus);

            // Nếu đã checkout rồi => fail
            if ("CHECKED_OUT".equalsIgnoreCase(currentStatus)) {
                item.addProperty("success", false);
                item.addProperty("message", "Vé đã được check-out trước đó");
                failCount++;
                resultArray.add(item);
                continue;
            }

            // Muốn checkout thì vé phải CHECKED_IN
            // Nếu chưa checkin hoặc trạng thái khác => fail
            if (!"CHECKED_IN".equalsIgnoreCase(currentStatus)) {
                item.addProperty("success", false);
                item.addProperty("message",
                        "Không thể check-out vì vé chưa check-in hoặc trạng thái không hợp lệ: " + currentStatus);
                failCount++;
                resultArray.add(item);
                continue;
            }

            // 5.4) Update DB: set status CHECKED_OUT + thời gian checkout (tuỳ DAO)
            boolean ok = ticketDAO.checkoutTicket(ticketId);

            if (!ok) {
                // Update thất bại => fail
                item.addProperty("success", false);
                item.addProperty("message", "Không thể check-out vé. Vui lòng thử lại");
                failCount++;
            } else {
                // Update ok => success
                item.addProperty("success", true);
                item.addProperty("status", "CHECKED_OUT");
                item.addProperty("checkoutTime", now.toString());
                item.addProperty("message", "Check-out thành công vé #" + ticketId);
                successCount++;
            }

            // add kết quả từng vé vào results[]
            resultArray.add(item);
        }

        // ===================== 6) Message tổng hợp =====================
        // isSuccess = true khi failCount == 0 (tức tất cả vé OK)
        boolean isSuccess = (failCount == 0);
        String mainMessage;

        /**
         * Nếu quét 1 vé: - message chính = message của vé đó (đỡ phải format)
         *
         * Nếu quét nhiều vé: - Nếu tất cả OK: "Check-out thành công X vé" - Nếu
         * tất cả fail: "Check-out thất bại cho tất cả X vé" - Nếu 1 phần:
         * "Check-out thành công a/b vé"
         */
        if (ticketIds.size() == 1) {
            JsonObject result = resultArray.get(0).getAsJsonObject();
            mainMessage = result.get("message").getAsString();
        } else {
            if (isSuccess) {
                mainMessage = String.format("Check-out thành công %d vé", successCount);
            } else if (successCount == 0) {
                mainMessage = String.format("Check-out thất bại cho tất cả %d vé", failCount);
            } else {
                mainMessage = String.format("Check-out thành công %d/%d vé", successCount, ticketIds.size());
            }
        }

        // ===================== 7) Response JSON =====================
        // Build object tổng cho FE
        JsonObject resJson = new JsonObject();
        resJson.addProperty("success", isSuccess);
        resJson.addProperty("message", mainMessage);
        resJson.addProperty("totalTickets", ticketIds.size());
        resJson.addProperty("successCount", successCount);
        resJson.addProperty("failCount", failCount);
        resJson.add("results", resultArray);

        /**
         * STATUS HTTP cuối: - Nếu tất cả vé OK => 200 - Nếu có bất kỳ vé fail
         * => 400
         *
         * CÂU HỎI THẦY/CÔ: 1) "Sao ticket không tồn tại không trả 404?" => Vì
         * bạn đang xử lý batch nhiều vé, nên gói lỗi vào results[] để FE vẫn
         * thấy từng vé fail gì. 2) "Có chuẩn REST không?" => Có thể cải tiến
         * dùng 207 Multi-Status (WebDAV) hoặc luôn 200 và dựa
         * successCount/failCount.
         */
        resp.setStatus(isSuccess ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST); // 200 or 400
        resp.getWriter().write(gson.toJson(resJson));
    }
}
