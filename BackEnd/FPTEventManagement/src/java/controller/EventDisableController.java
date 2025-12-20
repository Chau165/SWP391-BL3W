package controller;

import DAO.EventDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * API: /api/event/disable
 *
 * MỤC ĐÍCH:
 * - Vô hiệu hóa (disable) một event.
 * - Chỉ cho disable khi event CHƯA có ticket nào (để tránh huỷ sự kiện đã có người mua vé).
 *
 * ======================= STATUS CODE TRẢ VỀ =======================
 * ✅ GET:
 * - 200 OK: chỉ dùng để test servlet có mapping đúng hay chưa (trả text hướng dẫn).
 *
 * ✅ POST:
 * - 200 OK: disable event thành công
 * - 400 BAD_REQUEST:
 *     + thiếu eventId
 *     + eventId không parse được số (không hợp lệ)
 * - 404 NOT_FOUND:
 *     + Event không tồn tại (dao.disableEventIfNoTickets trả false)
 * - 409 CONFLICT:
 *     + Event đã có vé (business rule conflict: không được disable vì đã có ticket)
 * - 500 INTERNAL_SERVER_ERROR:
 *     + Lỗi hệ thống khác (DB lỗi, exception không mong muốn...)
 *
 * CÂU HỎI THẦY/CÔ HAY HỎI:
 * 1) Vì sao "event đã có vé" lại trả 409 mà không phải 400?
 *    => Vì request hợp lệ, nhưng xung đột với trạng thái tài nguyên (event đã có giao dịch).
 * 2) Vì sao event không tồn tại trả 404?
 *    => Vì resource eventId đó không tồn tại.
 * 3) Vì sao thêm GET?
 *    => để test nhanh mapping servlet khi debug (không phải nghiệp vụ chính).
 */
@WebServlet("/api/event/disable")
public class EventDisableController extends HttpServlet {

    /**
     * ✅ doGet:
     * - Chỉ để test nhanh đường dẫn servlet có hoạt động không.
     * - Khi mở URL bằng trình duyệt: /api/event/disable
     *   sẽ thấy text "OK..." (giúp biết servlet đã deploy/map đúng).
     *
     * STATUS:
     * - Mặc định 200 OK (không setStatus khác)
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Sao API disable lại có GET?" => để debug/test mapping, nghiệp vụ thật là POST.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Trả text/plain vì chỉ là thông báo test
        response.setContentType("text/plain; charset=UTF-8");

        // Gợi ý cho dev dùng POST với param eventId
        response.getWriter().write("EventDisableController OK. Use POST with eventId.");
    }

    /**
     * ✅ doPost:
     * FLOW:
     * 1) Đọc eventId từ query/form param
     * 2) Validate: có eventId không? parse int được không?
     * 3) Gọi DAO disableEventIfNoTickets(eventId)
     * 4) Trả kết quả:
     *    - OK => 200
     *    - Event không tồn tại => 404
     *    - Event đã có vé => 409
     *    - Lỗi khác => 500
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Sao dùng request.getParameter thay vì JSON body?" => đơn giản, gọi nhanh bằng form/query;
     *   nếu chuẩn REST hơn có thể dùng JSON body.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // API trả JSON
        response.setContentType("application/json; charset=UTF-8");

        // ===================== (1) Lấy param eventId =====================
        String idRaw = request.getParameter("eventId");

        // Nếu thiếu eventId => 400 Bad Request
        if (idRaw == null || idRaw.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            response.getWriter().write("{\"success\":false,\"message\":\"Thiếu eventId\"}");
            return;
        }

        // ===================== (2) Parse int eventId =====================
        int eventId;
        try {
            eventId = Integer.parseInt(idRaw.trim());
        } catch (NumberFormatException e) {
            // eventId không phải số => 400 Bad Request
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            response.getWriter().write("{\"success\":false,\"message\":\"eventId không hợp lệ\"}");
            return;
        }

        // ===================== (3) Gọi DAO xử lý business rule =====================
        EventDAO dao = new EventDAO();

        try {
            /**
             * disableEventIfNoTickets(eventId):
             * - Nếu event tồn tại và chưa có ticket => update status event thành DISABLED/CANCELLED... (tuỳ DB)
             * - Nếu event không tồn tại => return false
             * - Nếu event đã có ticket => throw Exception (message có chứa "đã có vé")
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Tại sao không trả true/false hết mà lại throw exception?"
             *   => Bạn đang tách 2 loại lỗi:
             *      + not found => return false (resource không tồn tại)
             *      + business rule conflict => throw exception để controller map ra 409
             *
             * (Gợi ý cải thiện nếu bị hỏi):
             * - Nên dùng custom exception (vd: TicketsExistException) thay vì check msg.contains(...)
             */
            boolean ok = dao.disableEventIfNoTickets(eventId);

            // ===================== (4) Check event tồn tại không =====================
            if (!ok) {
                // Event không tồn tại => 404 Not Found
                response.setStatus(HttpServletResponse.SC_NOT_FOUND); // 404
                response.getWriter().write("{\"success\":false,\"message\":\"Event không tồn tại\"}");
                return;
            }

            // ===================== (5) Success =====================
            response.setStatus(HttpServletResponse.SC_OK); // 200
            response.getWriter().write("{\"success\":true,\"message\":\"Vô hiệu hóa event thành công\"}");

        } catch (Exception ex) {
            // ===================== (6) Handle lỗi =====================

            // Lấy message lỗi để trả về client
            String msg = ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống";

            /**
             * Nếu msg chứa "đã có vé" => business rule conflict
             * STATUS: 409 Conflict
             *
             * Ngược lại => lỗi hệ thống / DB error
             * STATUS: 500 Internal Server Error
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Sao biết lỗi là đã có vé?" => hiện tại dựa vào message.
             * - "Cách tốt hơn?" => dùng custom exception / error code từ DAO.
             */
            if (msg.contains("đã có vé")) {
                response.setStatus(HttpServletResponse.SC_CONFLICT); // 409
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            }

            // Escape message để không phá JSON (nếu msg có dấu " hoặc \)
            response.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(msg) + "\"}");
        }
    }

    /**
     * escapeJson:
     * - Tránh JSON bị lỗi khi message có ký tự đặc biệt:
     *   \  hoặc  "
     *
     * CÂU HỎI THẦY/CÔ:
     * - "Sao phải escape?" => vì bạn đang tự nối string JSON bằng tay, nếu msg có " sẽ hỏng JSON.
     * - "Cách tốt hơn?" => dùng Gson để build object rồi gson.toJson().
     */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
