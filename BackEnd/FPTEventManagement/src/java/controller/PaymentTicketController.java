// controller/PaymentTicketController.java
package controller;

import config.VnPayUtil;

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

@WebServlet("/api/payment-ticket")
public class PaymentTicketController extends HttpServlet {

    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();
    private final SeatDAO seatDAO = new SeatDAO();
    private final TicketDAO ticketDAO = new TicketDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [PaymentTicketController] New request =====");
            System.out.println("QueryString: " + req.getQueryString());
            System.out.println("RemoteAddr: " + req.getRemoteAddr());

            // ================== Lấy tham số ==================
            String userIdStr = req.getParameter("userId");
            String eventIdStr = req.getParameter("eventId");

            // ✅ vẫn giữ param cũ để FE không phải đổi
            String categoryIdStr = req.getParameter("categoryTicketId");

            // ⚠️ nhiều ghế, dạng "1,2,3"
            String seatIdsStr = req.getParameter("seatIds");

            System.out.println("Raw params -> userId=" + userIdStr
                    + ", eventId=" + eventIdStr
                    + ", categoryTicketId=" + categoryIdStr
                    + ", seatIds=" + seatIdsStr);

            if (isBlank(userIdStr) || isBlank(eventIdStr) || isBlank(seatIdsStr)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Missing userId/eventId/seatIds");
                System.out.println("❌ Missing required params");
                return;
            }

            int userId = Integer.parseInt(userIdStr);
            int eventId = Integer.parseInt(eventIdStr);

            // Parse danh sách seatId
            List<Integer> seatIds = new ArrayList<>();
            for (String t : seatIdsStr.split(",")) {
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

            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();

            // ================== Validate ghế + tính tiền theo seatType ==================
            BigDecimal totalPrice = BigDecimal.ZERO;

            // Lưu categoryTicketId tương ứng với từng seat để insert ticket
            List<Integer> categoryIdsForSeats = new ArrayList<>();

            for (Integer seatId : seatIds) {
                Seat seat = eventSeatLayoutDAO.getSeatForEvent(eventId, seatId);
                System.out.println("[CHECK] Seat for event: " + seat);

                if (seat == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " not configured for this event");
                    System.out.println("❌ Seat " + seatId + " not configured for this event");
                    return;
                }

                if (event.getAreaId() != null && seat.getAreaId() != event.getAreaId()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " does not belong to event area");
                    System.out.println("❌ Seat " + seatId + " does not belong to event area");
                    return;
                }

                if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " is not AVAILABLE for this event");
                    System.out.println("❌ Seat " + seatId + " is not AVAILABLE, status=" + seat.getStatus());
                    return;
                }

                // ✅ BỎ RÀNG BUỘC "phải cùng 1 loại vé" -> không dùng ticketName chung nữa
                // => tính theo seatType của ghế
                String seatType = seat.getSeatType(); // VIP/STANDARD...
                if (isBlank(seatType)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " has no seatType");
                    System.out.println("❌ Seat has no seatType, seatId=" + seatId);
                    return;
                }

                // ✅ Lấy đúng CategoryTicket theo seatType để tính giá
                // Bạn cần DAO có hàm getActiveCategoryTicketByEventIdAndName(eventId, seatType)
                CategoryTicket ctByType = categoryDAO.getActiveCategoryTicketByEventIdAndName(eventId, seatType);
                System.out.println("[CHECK] CategoryTicket by seatType (" + seatType + "): " + ctByType);

                if (ctByType == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("No active category ticket for seatType: " + seatType);
                    System.out.println("❌ No active category ticket for seatType=" + seatType);
                    return;
                }

                categoryIdsForSeats.add(ctByType.getCategoryTicketId());
                // ✅ category id cho seat này
                totalPrice = totalPrice.add(ctByType.getPrice());
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
                for (int i = 0; i < seatIds.size(); i++) {
                    Integer seatId = seatIds.get(i);
                    Integer categoryIdForSeat = categoryIdsForSeats.get(i);

                    Ticket temp = new Ticket();
                    temp.setEventId(eventId);
                    temp.setUserId(userId);
                    temp.setCategoryTicketId(categoryIdForSeat); // ✅ đúng theo seatType
                    temp.setSeatId(seatId);
                    temp.setBillId(null);
                    temp.setStatus("PENDING");
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

            String tempTicketIdsStr = tempTicketIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            System.out.println("✅ Created PENDING tickets: " + tempTicketIdsStr);

            // ================== Tính tiền (✅ đúng theo từng ghế) ==================
            long amountVND = totalPrice.longValue();

            System.out.println("[PRICE] totalPrice = " + totalPrice
                    + " -> amountVND (long) = " + amountVND);

            // ================== Build orderInfo (gửi sang VNPay) ==================
            // Giữ lại categoryTicketId (param cũ) nếu FE vẫn gửi, nhưng thực tế ticket đã theo seatType
            // Thêm categoryTicketIdsUsed để debug/verify
            String categoryTicketIdsUsedStr = categoryIdsForSeats.stream()
                    .map(String::valueOf).collect(Collectors.joining(","));

            String orderInfo
                    = "userId=" + userId
                    + "&eventId=" + eventId
                    + "&categoryTicketId=" + (categoryIdStr == null ? "" : categoryIdStr)
                    + "&seatIds=" + seatIdsStr
                    + "&categoryTicketIdsUsed=" + categoryTicketIdsUsedStr
                    + "&tempTicketIds=" + tempTicketIdsStr
                    + "&orderType=buyTicket";

            System.out.println("[ORDER_INFO] " + orderInfo);

            String vnp_TxnRef = String.valueOf(System.currentTimeMillis());
            System.out.println("[TXN_REF] " + vnp_TxnRef);

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

            resp.sendRedirect(paymentUrl);

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().println("Lỗi thanh toán vé: " + e.toString());
            System.out.println("❌ Exception in PaymentTicketController: " + e.toString());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
