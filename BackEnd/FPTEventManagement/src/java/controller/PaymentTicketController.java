// controller/PaymentTicketController.java
package controller;

import config.VnPayConfig;
import config.VnPayUtil;

import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.SeatDAO;
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;

@WebServlet("/api/payment-ticket")
public class PaymentTicketController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [PaymentTicketController] New request =====");
            System.out.println("QueryString: " + req.getQueryString());
            System.out.println("RemoteAddr: " + req.getRemoteAddr());

            // ================== Lấy tham số ==================
            String userIdStr      = req.getParameter("userId");
            String eventIdStr     = req.getParameter("eventId");
            String categoryIdStr  = req.getParameter("categoryTicketId");
            String seatIdStr      = req.getParameter("seatId");

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

            int userId          = Integer.parseInt(userIdStr);
            int eventId         = Integer.parseInt(eventIdStr);
            int categoryId      = Integer.parseInt(categoryIdStr);
            int seatId          = Integer.parseInt(seatIdStr);

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

            // ================== Validate Seat ==================
            SeatDAO seatDAO = new SeatDAO();
            Seat seat = seatDAO.getSeatById(seatId);
            System.out.println("[CHECK] Seat: " + seat);

            // ✅ ĐỔI: so sánh theo areaId thay vì venueId
            if (seat == null
                    || event.getAreaId() == null
                    || seat.getAreaId() != event.getAreaId()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat not found or not belong to event area");
                System.out.println("❌ Seat not found or not belong to event area");
                return;
            }

            if (!"ACTIVE".equalsIgnoreCase(seat.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat is not ACTIVE");
                System.out.println("❌ Seat is not ACTIVE");
                return;
            }

            if (!seat.getSeatType().equalsIgnoreCase(ct.getName())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat type does not match ticket type");
                System.out.println("❌ Seat type does not match ticket type");
                return;
            }

            if (seatDAO.isSeatAlreadyBookedForEvent(eventId, seatId)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Seat already booked");
                System.out.println("❌ Seat already booked");
                return;
            }

            // ================== Tính tiền ==================
            BigDecimal price = ct.getPrice();      // ví dụ 150000.00
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
                    "other"        // vnp_OrderType: dùng "other" cho an toàn
            );

            System.out.println("===== [PaymentTicketController] Redirect VNPay URL =====");
            System.out.println(paymentUrl);
            System.out.println("=========================================================");

            resp.sendRedirect(paymentUrl);

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().println("Lỗi thanh toán vé: " + e.getMessage());
            System.out.println("❌ Exception in PaymentTicketController: " + e.getMessage());
        }
    }
}
