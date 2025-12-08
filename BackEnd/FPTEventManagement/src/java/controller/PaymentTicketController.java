// controller/PaymentTicketController.java
package controller;

import config.VnPayConfig;
import config.VnPayUtil;

import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.EventSeatLayoutDAO;   // ✅ dùng layout theo event
import DAO.SeatDAO;             // optional, chỉ để double-check Ticket
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/api/payment-ticket")
public class PaymentTicketController extends HttpServlet {

    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();
    private final SeatDAO seatDAO = new SeatDAO(); // chỉ dùng hàm isSeatAlreadyBookedForEvent (nếu muốn)

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
            String seatIdStr     = req.getParameter("seatId");

            System.out.println("Raw params -> userId=" + userIdStr
                    + ", eventId=" + eventIdStr
                    + ", categoryTicketId=" + categoryIdStr
                    + ", seatId=" + seatIdStr);

            if (userIdStr == null || eventIdStr == null
                    || categoryIdStr == null || seatIdStr == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Missing userId/eventId/categoryTicketId/seatId");
                System.out.println("❌ Missing required params");
                return;
            }

            int userId     = Integer.parseInt(userIdStr);
            int eventId    = Integer.parseInt(eventIdStr);
            int categoryId = Integer.parseInt(categoryIdStr);
            int seatId     = Integer.parseInt(seatIdStr);

            System.out.println("Parsed params -> userId=" + userId
                    + ", eventId=" + eventId
                    + ", categoryId=" + categoryId
                    + ", seatId=" + seatId);

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

            // ================== Validate Seat (theo layout event) ==================
            // ❗❗ LẤY GHẾ TỪ Event_Seat_Layout + Seat
            Seat seat = eventSeatLayoutDAO.getSeatForEvent(eventId, seatId);
            System.out.println("[CHECK] Seat for event: " + seat);

            if (seat == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat not configured for this event");
                System.out.println("❌ Seat not configured for this event");
                return;
            }

            // optional: check ghế có thuộc area của event không
            if (event.getAreaId() != null && seat.getAreaId() != event.getAreaId()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat does not belong to event area");
                System.out.println("❌ Seat does not belong to event area");
                return;
            }

            // status ở đây là layout_status: AVAILABLE / BOOKED
            if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat is not AVAILABLE for this event");
                System.out.println("❌ Seat is not AVAILABLE for this event, status=" + seat.getStatus());
                return;
            }

            // So sánh loại ghế: seat_type (VIP/STANDARD) vs CategoryTicket.name (ví dụ "VIP", "STANDARD")
            String seatType = seat.getSeatType();      // lấy từ Event_Seat_Layout
            String ticketName = ct.getName();          // "VIP", "Standard", "Vé VIP",...

            if (seatType == null || ticketName == null
                    || !seatType.equalsIgnoreCase(ticketName.trim())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat type does not match ticket type");
                System.out.println("❌ Seat type mismatch. seatType=" + seatType + ", ticketName=" + ticketName);
                return;
            }

            // (optional) double-check bằng Ticket table
            if (seatDAO.isSeatAlreadyBookedForEvent(eventId, seatId)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat already booked");
                System.out.println("❌ Seat already booked (check from Ticket table)");
                return;
            }

            // ================== Tính tiền ==================
            BigDecimal price = ct.getPrice();    // ví dụ 150000.00
            long amountVND   = price.longValue(); // 150000

            System.out.println("[PRICE] Ticket price(BigDecimal) = " + price
                    + " -> amountVND (long) = " + amountVND);

            // ================== Build orderInfo (gửi sang VNPay) ==================
            String orderInfo =
                    "userId=" + userId +
                    "&eventId=" + eventId +
                    "&categoryTicketId=" + categoryId +
                    "&seatId=" + seatId +
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
            // in cả toString cho dễ debug loại Exception
            resp.getWriter().println("Lỗi thanh toán vé: " + e.toString());
            System.out.println("❌ Exception in PaymentTicketController: " + e.toString());
        }
    }
}
