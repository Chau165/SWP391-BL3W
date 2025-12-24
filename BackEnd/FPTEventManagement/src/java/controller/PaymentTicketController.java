// controller/PaymentTicketController.java
package controller;

import config.VnPayUtil; // Util tạo URL thanh toán VNPay (build params + ký HMAC + returnUrl)

import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.EventSeatLayoutDAO;
import DAO.SeatDAO;
import DAO.TicketDAO;

import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;
import DTO.Ticket;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API: GET /api/payment-ticket
 *
 * MỤC ĐÍCH:
 * - Đây là API "KHỞI TẠO THANH TOÁN": user chọn ghế -> backend kiểm tra hợp lệ -> tạo Ticket PENDING để giữ ghế
 *   -> build paymentUrl -> redirect user sang trang VNPay để thanh toán.
 *
 * AI GỌI:
 * - Frontend (khi student bấm "Thanh toán / Pay with VNPay").
 *
 * SAU KHI THANH TOÁN:
 * - VNPay sẽ redirect về /api/buyTicket (BuyTicketController) để confirm & update PENDING -> BOOKED.
 *
 * STATUS CODE TỔNG QUAN:
 * - 302 Found: redirect sang VNPay (resp.sendRedirect(paymentUrl))  ✅ (thành công)
 * - 400 Bad Request: thiếu params / event sai / seat sai / seat không available / thiếu seatType / thiếu categoryTicket cho seatType
 * - 409 Conflict: tạo PENDING ticket fail do tranh chấp ghế (race condition) hoặc insert lỗi -> trả thông báo chọn ghế khác
 * - 500 Internal Server Error: lỗi server khi check booked seats (DB/exception)
 * - (catch-all cuối) hiện code chưa set status -> mặc định có thể vẫn 200 nếu chỉ println text
 *
 * CÂU HỎI THẦY/CÔ HAY HỎI:
 * - "Tại sao tạo ticket PENDING trước khi redirect sang VNPay?"
 * - "Race condition xử lý thế nào nếu 2 người chọn cùng ghế?"
 * - "Tại sao check ghế ở cả EventSeatLayout và Ticket?"
 * - "Vì sao dùng GET mà không POST?"
 * - "Status code nào trả ra cho từng trường hợp?"
 */
@WebServlet("/api/payment-ticket")
public class PaymentTicketController extends HttpServlet {

    // DAO lấy seat layout theo event (event_id + seat_id + seat_type + status)
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();

    // SeatDAO ở đây dùng để check ghế đã bị giữ/đặt trong bảng Ticket (PENDING/BOOKED) - chống tranh ghế
    private final SeatDAO seatDAO = new SeatDAO();

    // TicketDAO: insert PENDING ticket + delete khi lỗi
    private final TicketDAO ticketDAO = new TicketDAO();

    /**
     * doGet:
     * FLOW CHẠY (mang đi thuyết trình):
     * 1) Nhận params (userId, eventId, seatIds, categoryTicketId cũ)
     * 2) Validate input basic
     * 3) Validate event (phải OPEN)
     * 4) Với mỗi seatId:
     *    - check seat có trong layout event
     *    - check seat thuộc area của event
     *    - check seat status AVAILABLE
     *    - lấy seatType -> map sang CategoryTicket -> cộng tiền
     * 5) Double-check ghế đã bị giữ/đặt trong Ticket table chưa (PENDING/BOOKED)
     * 6) Insert tickets PENDING để giữ chỗ (tempTicketIds)
     * 7) Build orderInfo + paymentUrl VNPay
     * 8) Redirect user sang VNPay (302)
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // Trả text/plain để debug log (thực tế main output là redirect 302)
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [PaymentTicketController] New request =====");
            System.out.println("QueryString: " + req.getQueryString());
            System.out.println("RemoteAddr: " + req.getRemoteAddr());

            // ================== (1) Lấy tham số từ FE ==================
            String userIdStr = req.getParameter("userId");     // userId FE truyền lên (NOTE: thầy hay hỏi bảo mật)
            String eventIdStr = req.getParameter("eventId");   // eventId FE truyền lên

            // ✅ vẫn giữ param cũ để FE không phải đổi
            String categoryIdStr = req.getParameter("categoryTicketId"); // hiện không dùng để tính tiền nữa

            // ⚠️ nhiều ghế, dạng "1,2,3"
            String seatIdsStr = req.getParameter("seatIds");

            System.out.println("Raw params -> userId=" + userIdStr
                    + ", eventId=" + eventIdStr
                    + ", categoryTicketId=" + categoryIdStr
                    + ", seatIds=" + seatIdsStr);

            /**
             * Nếu thiếu param bắt buộc
             *
             * ✅ STATUS CODE: 400 Bad Request
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Tại sao 400?" => vì client gửi thiếu dữ liệu
             * - "Sao userId lại lấy từ param chứ không từ token?" (đây là điểm thầy có thể xoáy)
             */
            if (isBlank(userIdStr) || isBlank(eventIdStr) || isBlank(seatIdsStr)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                resp.getWriter().println("Missing userId/eventId/seatIds");
                System.out.println("❌ Missing required params");
                return;
            }

            // Parse int (nếu parse fail sẽ nhảy xuống catch cuối)
            int userId = Integer.parseInt(userIdStr);
            int eventId = Integer.parseInt(eventIdStr);

            // ================== (2) Parse danh sách seatIds ==================
            List<Integer> seatIds = new ArrayList<>();
            for (String t : seatIdsStr.split(",")) {
                if (t != null && !t.trim().isEmpty()) {
                    seatIds.add(Integer.parseInt(t.trim()));
                }
            }

            /**
             * Nếu seatIds parse ra rỗng
             *
             * ✅ STATUS CODE: 400 Bad Request
             */
            if (seatIds.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                resp.getWriter().println("seatIds is empty");
                System.out.println("❌ seatIds is empty");
                return;
            }

            System.out.println("Parsed params -> userId=" + userId
                    + ", eventId=" + eventId
                    + ", seatIds=" + seatIds);

            // ================== (3) Validate Event ==================
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);
            System.out.println("[CHECK] Event: " + event);

            /**
             * Event không tồn tại hoặc không OPEN => không được mua
             *
             * ✅ STATUS CODE: 400 Bad Request
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Vì sao chỉ OPEN mới cho mua?" => CLOSED thì đóng bán, tránh thanh toán sai.
             */
            if (event == null || !"OPEN".equalsIgnoreCase(event.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                resp.getWriter().println("Event not found or not OPEN");
                System.out.println("❌ Event not found or not OPEN");
                return;
            }

            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();

            // ================== (4) Validate ghế + tính tiền theo seatType ==================
            BigDecimal totalPrice = BigDecimal.ZERO;

            // Mỗi seat có thể có categoryTicketId riêng theo seatType (VIP/Standard)
            List<Integer> categoryIdsForSeats = new ArrayList<>();

            for (Integer seatId : seatIds) {

                /**
                 * Lấy seat "theo event" (JOIN Event_Seat_Layout + Seat):
                 * - đảm bảo seatId này được cấu hình cho event
                 * - lấy seatType + status theo event
                 *
                 * CÂU HỎI THẦY/CÔ:
                 * - "Tại sao không lấy từ bảng Seat luôn?" => vì Seat là ghế vật lý, còn event có cấu hình riêng (seat_type/status)
                 */
                Seat seat = eventSeatLayoutDAO.getSeatForEvent(eventId, seatId);
                System.out.println("[CHECK] Seat for event: " + seat);

                // seat không nằm trong layout của event => không hợp lệ
                if (seat == null) {
                    // ✅ STATUS CODE: 400 Bad Request
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    resp.getWriter().println("Seat " + seatId + " not configured for this event");
                    System.out.println("❌ Seat " + seatId + " not configured for this event");
                    return;
                }

                // Seat không thuộc area của event => sai khu vực (để tránh seatId bậy)
                if (event.getAreaId() != null && seat.getAreaId() != event.getAreaId()) {
                    // ✅ STATUS CODE: 400 Bad Request
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    resp.getWriter().println("Seat " + seatId + " does not belong to event area");
                    System.out.println("❌ Seat " + seatId + " does not belong to event area");
                    return;
                }

                /**
                 * Check status seat trong layout event phải AVAILABLE (mở bán)
                 *
                 * ✅ STATUS CODE: 400 Bad Request
                 *
                 * CÂU HỎI THẦY/CÔ:
                 * - "Seat status AVAILABLE khác gì BOOKED?" => AVAILABLE là trạng thái cấu hình mở bán,
                 *   BOOKED là trạng thái giao dịch nằm ở Ticket.
                 */
                if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    resp.getWriter().println("Seat " + seatId + " is not AVAILABLE for this event");
                    System.out.println("❌ Seat " + seatId + " is not AVAILABLE, status=" + seat.getStatus());
                    return;
                }

                // seatType dùng để map giá đúng (VIP/Standard)
                String seatType = seat.getSeatType();
                if (isBlank(seatType)) {
                    // ✅ STATUS CODE: 400 Bad Request
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    resp.getWriter().println("Seat " + seatId + " has no seatType");
                    System.out.println("❌ Seat has no seatType, seatId=" + seatId);
                    return;
                }

                /**
                 * Lấy CategoryTicket theo seatType của ghế (VIP/Standard)
                 *
                 * CÂU HỎI THẦY/CÔ:
                 * - "Vì sao không bắt user chọn 1 categoryTicketId chung?"
                 *   => Vì hệ thống cho phép user chọn nhiều ghế khác loại (VIP + Standard) trong 1 lần thanh toán.
                 */
                CategoryTicket ctByType = categoryDAO.getActiveCategoryTicketByEventIdAndName(eventId, seatType);
                System.out.println("[CHECK] CategoryTicket by seatType (" + seatType + "): " + ctByType);

                if (ctByType == null) {
                    // ✅ STATUS CODE: 400 Bad Request
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    resp.getWriter().println("No active category ticket for seatType: " + seatType);
                    System.out.println("❌ No active category ticket for seatType=" + seatType);
                    return;
                }

                // Lưu categoryTicketId tương ứng seat này để lát insert Ticket
                categoryIdsForSeats.add(ctByType.getCategoryTicketId());

                // Cộng giá theo từng ghế
                totalPrice = totalPrice.add(ctByType.getPrice());
            }

            // ================== (5) Double-check trên Ticket table (PENDING/BOOKED) ==================
            /**
             * Mục tiêu:
             * - Chống race condition: 2 người cùng chọn 1 ghế trong cùng thời điểm.
             * - Event_Seat_Layout chỉ là cấu hình (AVAILABLE), còn ai đang giữ/chốt ghế nằm ở Ticket table.
             *
             * ✅ Nếu phát hiện ghế đã bị giữ/đặt => 400 Bad Request
             * ✅ Nếu lỗi DB khi check => 500 Internal Server Error
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Tại sao check ở Ticket chứ không update status ở Event_Seat_Layout?"
             *   => Ticket là bảng giao dịch (PENDING/BOOKED), Event_Seat_Layout là cấu hình mở bán.
             */
            try {
                //Bước này dùng để kiểm tra trong bảng Ticket xem có vé nào đã tồn tại cho các seat_id đó hay chưa
                List<Integer> alreadyBookedSeatIds = seatDAO.findAlreadyBookedSeatIdsForEvent(eventId, seatIds);
                if (!alreadyBookedSeatIds.isEmpty()) {
                    // ✅ STATUS CODE: 400 Bad Request
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // = 400
                    String msg = "Some seats already reserved/booked: " + alreadyBookedSeatIds;
                    resp.getWriter().println(msg);
                    System.out.println("❌ " + msg);
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                // ✅ STATUS CODE: 500 Internal Server Error (backend/DB lỗi khi check)
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // = 500
                resp.getWriter().println("Error checking booked seats: " + ex.toString());
                System.out.println("❌ Error checking booked seats: " + ex.toString());
                return;
            }

            // ================== (6) TẠO TICKET PENDING để giữ chỗ ==================
            /**
             * Đây là bước quan trọng nhất:
             * - Insert Ticket với status = PENDING trước khi redirect VNPay
             * - Nếu user thanh toán thành công -> BuyTicketController update PENDING -> BOOKED
             * - Nếu fail -> xoá PENDING để trả ghế
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Tại sao không đợi VNPay success rồi mới insert ticket?"
             *   => Nếu không tạo PENDING, nhiều người có thể thanh toán cùng 1 ghế (race condition).
             */
            List<Integer> tempTicketIds = new ArrayList<>();
            Timestamp now = new Timestamp(System.currentTimeMillis()); // (hiện chưa dùng)

            try {
                for (int i = 0; i < seatIds.size(); i++) {
                    Integer seatId = seatIds.get(i);
                    Integer categoryIdForSeat = categoryIdsForSeats.get(i);

                    Ticket temp = new Ticket();
                    temp.setEventId(eventId);
                    temp.setUserId(userId);
                    temp.setCategoryTicketId(categoryIdForSeat); // ✅ theo seatType của ghế
                    temp.setSeatId(seatId);
                    temp.setBillId(null);
                    temp.setStatus("PENDING"); // ✅ giữ chỗ
                    temp.setQrIssuedAt(null);
                    temp.setQrCodeValue(null);

                    // Insert ticket và lấy ticketId mới
                    int tid = ticketDAO.insertTicketAndReturnId(temp);
                    if (tid <= 0) {
                        throw new SQLException("insertTicketAndReturnId trả về <= 0 cho seatId=" + seatId);
                    }
                    tempTicketIds.add(tid);
                }
            } catch (Exception ex) {
                ex.printStackTrace();

                // Nếu đã insert được một phần -> rollback thủ công bằng cách xoá các ticket đã tạo
                if (!tempTicketIds.isEmpty()) {
                    try {
                        ticketDAO.deleteTicketsByIds(tempTicketIds);
                    } catch (Exception ex2) {
                        ex2.printStackTrace();
                    }
                }

                /**
                 * Nếu tạo PENDING thất bại (thường do ghế bị user khác giữ trước / unique constraint)
                 *
                 * ✅ STATUS CODE: 409 Conflict
                 * - Conflict nghĩa là xung đột tài nguyên (ghế)
                 *
                 * CÂU HỎI THẦY/CÔ:
                 * - "Sao ở trên trả 400, ở đây trả 409?" =>
                 *   + 400: input/logic không hợp lệ
                 *   + 409: xung đột do concurrency (ghế bị người khác giữ trước)
                 */
                resp.setStatus(HttpServletResponse.SC_CONFLICT); // = 409
                resp.getWriter().println("Seat(s) already taken by another user. Please choose other seats.");
                System.out.println("❌ Error creating PENDING tickets: " + ex.toString());
                return;
            }

            // Chuyển tempTicketIds sang "1,2,3" để nhét vào orderInfo
            String tempTicketIdsStr = tempTicketIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            System.out.println("✅ Created PENDING tickets: " + tempTicketIdsStr);

            // ================== (7) Tính tiền & build orderInfo ==================
            long amountVND = totalPrice.longValue(); // VNPay nhận amount theo VND (số nguyên)

            System.out.println("[PRICE] totalPrice = " + totalPrice
                    + " -> amountVND (long) = " + amountVND);

            /**
             * orderInfo là chuỗi "metadata" gửi qua VNPay và VNPay trả lại ở /api/buyTicket
             * để backend biết thanh toán này thuộc user/event/ghế/ticket nào.
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Trong orderInfo em nhét gì và để làm gì?"
             *   => userId, eventId, seatIds, tempTicketIds ... để xác nhận & update BOOKED sau thanh toán.
             */
            String categoryTicketIdsUsedStr = categoryIdsForSeats.stream()
                    .map(String::valueOf).collect(Collectors.joining(","));

            String orderInfo
                    = "userId=" + userId
                    + "&eventId=" + eventId
                    + "&categoryTicketId=" + (categoryIdStr == null ? "" : categoryIdStr) // giữ param cũ
                    + "&seatIds=" + seatIdsStr
                    + "&categoryTicketIdsUsed=" + categoryTicketIdsUsedStr // debug/verify
                    + "&tempTicketIds=" + tempTicketIdsStr
                    + "&orderType=buyTicket";

            System.out.println("[ORDER_INFO] " + orderInfo);

            // TxnRef: mã giao dịch (dùng timestamp làm unique)
            String vnp_TxnRef = String.valueOf(System.currentTimeMillis());
            System.out.println("[TXN_REF] " + vnp_TxnRef);

            // ================== (8) Tạo URL VNPay và redirect ==================
            String paymentUrl = VnPayUtil.createPaymentUrl(
                    req,
                    vnp_TxnRef,
                    amountVND,
                    orderInfo,
                    "other"
            );

            System.out.println("===== [PaymentTicketController] Redirect VNPay URL =====");
            System.out.println(paymentUrl);
            System.out.println("=========================================================");

            /**
             * Thành công -> redirect sang VNPay
             *
             * ✅ STATUS CODE: 302 Found (redirect)
             *
             * CÂU HỎI THẦY/CÔ:
             * - "Vì sao response lại không trả JSON mà redirect?"
             *   => Vì flow VNPay là user browser cần được chuyển qua cổng thanh toán.
             */
            resp.sendRedirect(paymentUrl); // = 302

        } catch (Exception e) {
            e.printStackTrace();

            /**
             * Catch-all:
             * - Hiện code chỉ println text, chưa set status => có thể default 200.
             * - Nếu thầy hỏi, bạn nói: "nên set 500 ở đây để chuẩn REST"
             */
            resp.getWriter().println("Lỗi thanh toán vé: " + e.toString());
            System.out.println("❌ Exception in PaymentTicketController: " + e.toString());
        }
    }

    // Helper check string rỗng/null
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
