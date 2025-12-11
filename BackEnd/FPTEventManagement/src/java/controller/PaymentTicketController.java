// controller/PaymentTicketController.java
package controller;

import config.VnPayConfig;
import config.VnPayUtil;

import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.EventSeatLayoutDAO;   // dùng layout theo event
import DAO.SeatDAO;             // dùng thêm hàm findAlreadyBookedSeatIdsForEvent
import DAO.TicketDAO;           // ✅ thêm
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;
import DTO.Ticket;              // ✅ thêm

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;   // ✅ thêm

@WebServlet("/api/payment-ticket")
public class PaymentTicketController extends HttpServlet {

    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();
    private final SeatDAO seatDAO = new SeatDAO();
    private final TicketDAO ticketDAO = new TicketDAO(); // ✅ dùng tạo ticket PENDING

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [PaymentTicketController] New request =====");
            System.out.println("QueryString: " + req.getQueryString());
            System.out.println("RemoteAddr: " + req.getRemoteAddr());

            // ================== Lấy tham số ==================
            String userIdStr     = req.getParameter("userId");
            String eventIdStr    = req.getParameter("eventId");
            String categoryIdStr = req.getParameter("categoryTicketId");
            // ⚠️ nhiều ghế, dạng "1,2,3"
            String seatIdsStr    = req.getParameter("seatIds");

            System.out.println("Raw params -> userId=" + userIdStr
                    + ", eventId=" + eventIdStr
                    + ", categoryTicketId=" + categoryIdStr
                    + ", seatIds=" + seatIdsStr);

            if (isBlank(userIdStr) || isBlank(eventIdStr)
                    || isBlank(categoryIdStr) || isBlank(seatIdsStr)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Missing userId/eventId/categoryTicketId/seatIds");
                System.out.println("❌ Missing required params");
                return;
            }

            int userId     = Integer.parseInt(userIdStr);
            int eventId    = Integer.parseInt(eventIdStr);
            int categoryId = Integer.parseInt(categoryIdStr);

            // Parse danh sách seatId
            String[] tokens = seatIdsStr.split(",");
            List<Integer> seatIds = new ArrayList<>();
            for (String t : tokens) {
                if (t != null && !t.trim().isEmpty()) {
                    seatIds.add(Integer.parseInt(t.trim()));
                }
            }

            if (seatIds.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("seatIds is empty");
                System.out.println("❌ seatIds is empty");
                return;
            }

            System.out.println("Parsed params -> userId=" + userId
                    + ", eventId=" + eventId
                    + ", categoryId=" + categoryId
                    + ", seatIds=" + seatIds);

            // ================== Validate Event ==================
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);
            System.out.println("[CHECK] Event: " + event);

            if (event == null || !"OPEN".equalsIgnoreCase(event.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Event not found or not OPEN");
                System.out.println("❌ Event not found or not OPEN");
                return;
            }

            // ================== Validate Category Ticket ==================
            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
            CategoryTicket ct = categoryDAO.getActiveCategoryTicketById(categoryId);
            System.out.println("[CHECK] CategoryTicket: " + ct);

            if (ct == null || ct.getEventId() != eventId) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Category ticket not valid");
                System.out.println("❌ Category ticket not valid");
                return;
            }

            String ticketName = ct.getName();  // "VIP", "STANDARD", ...

            // ================== Validate từng ghế theo layout event ==================
            for (Integer seatId : seatIds) {
                // LẤY GHẾ TỪ Event_Seat_Layout + Seat
                Seat seat = eventSeatLayoutDAO.getSeatForEvent(eventId, seatId);
                System.out.println("[CHECK] Seat for event: " + seat);

                if (seat == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " not configured for this event");
                    System.out.println("❌ Seat " + seatId + " not configured for this event");
                    return;
                }

                // optional: check ghế có thuộc area của event không
                if (event.getAreaId() != null && seat.getAreaId() != event.getAreaId()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " does not belong to event area");
                    System.out.println("❌ Seat " + seatId + " does not belong to event area");
                    return;
                }

                // status ở đây là layout_status: AVAILABLE / BOOKED (nếu bạn có cột này)
                if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " is not AVAILABLE for this event");
                    System.out.println("❌ Seat " + seatId + " is not AVAILABLE, status=" + seat.getStatus());
                    return;
                }

                // So sánh loại ghế: seat_type (VIP/STANDARD) vs CategoryTicket.name
                String seatType = seat.getSeatType(); // lấy từ Event_Seat_Layout
                if (seatType == null || ticketName == null
                        || !seatType.equalsIgnoreCase(ticketName.trim())) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " type does not match ticket type");
                    System.out.println("❌ Seat type mismatch. seatId=" + seatId
                            + ", seatType=" + seatType + ", ticketName=" + ticketName);
                    return;
                }
            }

            // ================== Double-check trên Ticket table (đã có ai giữ/chốt chưa) ==================
            try {
                List<Integer> alreadyBookedSeatIds = seatDAO.findAlreadyBookedSeatIdsForEvent(eventId, seatIds);
                if (!alreadyBookedSeatIds.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    String msg = "Some seats already reserved/booked: " + alreadyBookedSeatIds;
                    resp.getWriter().println(msg);
                    System.out.println("❌ " + msg);
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().println("Error checking booked seats: " + ex.toString());
                System.out.println("❌ Error checking booked seats: " + ex.toString());
                return;
            }

            // ================== TẠO TICKET GIẢ (PENDING) ĐỂ GIỮ CHỖ ==================
            List<Integer> tempTicketIds = new ArrayList<>();
            Timestamp now = new Timestamp(System.currentTimeMillis());

            try {
                for (Integer seatId : seatIds) {
                    Ticket temp = new Ticket();
                    temp.setEventId(eventId);
                    temp.setUserId(userId);
                    temp.setCategoryTicketId(categoryId);
                    temp.setSeatId(seatId);
                    temp.setBillId(null);          // chưa có bill
                    temp.setStatus("PENDING");     // ⚠️ nhớ cho phép trong CHECK constraint
                    temp.setQrIssuedAt(null);
                    temp.setQrCodeValue(null);

                    int tid = ticketDAO.insertTicketAndReturnId(temp);
                    if (tid <= 0) {
                        throw new SQLException("insertTicketAndReturnId trả về <= 0 cho seatId=" + seatId);
                    }
                    tempTicketIds.add(tid);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                // nếu lỗi unique (ghế vừa bị người khác giữ/trước đó), xoá hết ticket vừa tạo
                if (!tempTicketIds.isEmpty()) {
                    try {
                        ticketDAO.deleteTicketsByIds(tempTicketIds);
                    } catch (Exception ex2) {
                        ex2.printStackTrace();
                    }
                }

                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                resp.getWriter().println("Seat(s) already taken by another user. Please choose other seats.");
                System.out.println("❌ Error creating PENDING tickets: " + ex.toString());
                return;
            }

            String tempTicketIdsStr = tempTicketIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            System.out.println("✅ Created PENDING tickets: " + tempTicketIdsStr);

            // ================== Tính tiền (theo số ghế) ==================
            BigDecimal unitPrice = ct.getPrice();                       // giá 1 vé
            BigDecimal totalPrice = unitPrice.multiply(
                    BigDecimal.valueOf(seatIds.size()));               // tổng
            long amountVND = totalPrice.longValue();                    // cho VNPay

            System.out.println("[PRICE] unitPrice = " + unitPrice
                    + ", quantity = " + seatIds.size()
                    + " -> totalPrice = " + totalPrice
                    + " -> amountVND (long) = " + amountVND);

            // ================== Build orderInfo (gửi sang VNPay) ==================
            String orderInfo =
                    "userId=" + userId +
                    "&eventId=" + eventId +
                    "&categoryTicketId=" + categoryId +
                    "&seatIds=" + seatIdsStr +
                    "&tempTicketIds=" + tempTicketIdsStr +   // ✅ quan trọng
                    "&orderType=buyTicket";

            System.out.println("[ORDER_INFO] " + orderInfo);

            // Mã giao dịch unique
            String vnp_TxnRef = String.valueOf(System.currentTimeMillis());
            System.out.println("[TXN_REF] " + vnp_TxnRef);

            // ================== GỌI UTIL TẠO URL VNPAY ==================
            String paymentUrl = VnPayUtil.createPaymentUrl(
                    req,
                    vnp_TxnRef,
                    amountVND,
                    orderInfo,
                    "other"        // vnp_OrderType
            );

            System.out.println("===== [PaymentTicketController] Redirect VNPay URL =====");
            System.out.println(paymentUrl);
            System.out.println("=========================================================");

            resp.sendRedirect(paymentUrl);

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().println("Lỗi thanh toán vé: " + e.toString());
            System.out.println("❌ Exception in PaymentTicketController: " + e.toString());
        }
    }

    // helper thay cho String.isBlank() (Java 8)
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
