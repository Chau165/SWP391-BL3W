package controller;

import config.VnPayConfig;
import config.VnPayUtil;

import DAO.BillDAO;
import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.SeatDAO;
import DAO.TicketDAO;

import DTO.Bill;
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;
import DTO.Ticket;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

import utils.QRCodeUtil; // nhớ import đúng package QRCodeUtil của bạn

@WebServlet("/api/buyTicket")
public class BuyTicketController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [BuyTicketController] VNPay return =====");
            System.out.println("QueryString: " + req.getQueryString());

            // ===== 1. Lấy tất cả params từ VNPay =====
            Map<String, String> vnp_Params = new HashMap<>();
            Map<String, String[]> paramMap = req.getParameterMap();
            for (String key : paramMap.keySet()) {
                String[] values = paramMap.get(key);
                if (values != null && values.length > 0) {
                    vnp_Params.put(key, values[0]);
                }
            }

            String vnp_SecureHash = vnp_Params.get("vnp_SecureHash");
            vnp_Params.remove("vnp_SecureHash");
            vnp_Params.remove("vnp_SecureHashType");

            // ===== 2. Build lại hashData để verify chữ ký =====
            List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
            Collections.sort(fieldNames);
            StringBuilder hashData = new StringBuilder();

            for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext(); ) {
                String fieldName = itr.next();
                String fieldValue = vnp_Params.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    hashData.append(fieldName).append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    if (itr.hasNext()) {
                        hashData.append('&');
                    }
                }
            }

            String signValue = VnPayUtil.hmacSHA512(VnPayConfig.vnp_HashSecret, hashData.toString());
            System.out.println("[SIGN] local  = " + signValue);
            System.out.println("[SIGN] remote = " + vnp_SecureHash);

            if (!signValue.equals(vnp_SecureHash)) {
                resp.getWriter().println("❌ Chữ ký VNPay không hợp lệ!");
                System.out.println("❌ Invalid VNPay signature.");
                return;
            }

            // ===== 3. Kiểm tra mã phản hồi =====
            String responseCode = vnp_Params.get("vnp_ResponseCode");
            System.out.println("[VNPay] vnp_ResponseCode = " + responseCode);

            if (!"00".equals(responseCode)) {
                resp.getWriter().println("❌ Thanh toán thất bại! Mã lỗi: " + responseCode);
                return;
            }

            // ===== 4. Lấy thông tin orderInfo =====
            String orderInfoRaw = vnp_Params.get("vnp_OrderInfo");
            String orderInfo = URLDecoder.decode(orderInfoRaw, StandardCharsets.UTF_8.toString());
            System.out.println("[ORDER_INFO RAW] " + orderInfoRaw);
            System.out.println("[ORDER_INFO DEC] " + orderInfo);

            Map<String, String> infoMap = parseOrderInfo(orderInfo);

            int userId           = Integer.parseInt(infoMap.get("userId"));
            int eventId          = Integer.parseInt(infoMap.get("eventId"));
            int categoryTicketId = Integer.parseInt(infoMap.get("categoryTicketId"));
            int seatId           = Integer.parseInt(infoMap.get("seatId"));

            System.out.println("[PARSED] userId=" + userId +
                    ", eventId=" + eventId +
                    ", categoryTicketId=" + categoryTicketId +
                    ", seatId=" + seatId);

            // ===== 5. Re-validate business =====
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);
            if (event == null || !"OPEN".equalsIgnoreCase(event.getStatus())) {
                resp.getWriter().println("⚠️ Event not found or not OPEN.");
                System.out.println("⚠️ Event not found or not OPEN.");
                return;
            }

            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
            CategoryTicket ct = categoryDAO.getActiveCategoryTicketById(categoryTicketId);
            if (ct == null || ct.getEventId() != eventId) {
                resp.getWriter().println("⚠️ Category ticket invalid.");
                System.out.println("⚠️ Category ticket invalid.");
                return;
            }

            SeatDAO seatDAO = new SeatDAO();
            Seat seat = seatDAO.getSeatById(seatId);

            if (seat == null
                    || event.getAreaId() == null
                    || !event.getAreaId().equals(seat.getAreaId())) {
                resp.getWriter().println("⚠️ Seat invalid (không thuộc đúng khu vực của event).");
                System.out.println("⚠️ Seat invalid for event area.");
                return;
            }

            // Ghế phải là ghế vật lý đang được phép sử dụng
            if (!"ACTIVE".equalsIgnoreCase(seat.getStatus())) {
                resp.getWriter().println("⚠️ Ghế này đang bị khóa / không khả dụng.");
                System.out.println("⚠️ Seat is not ACTIVE (physically unavailable).");
                return;
            }

            // Không cho double-book trong CÙNG 1 event
            if (seatDAO.isSeatAlreadyBookedForEvent(eventId, seatId)) {
                resp.getWriter().println("⚠️ Ghế này đã được đặt cho event này.");
                System.out.println("⚠️ Seat already booked for this event.");
                return;
            }

            // ===== 6. Tạo Bill (PAID) =====
            // vnp_Amount trả về theo đơn vị "xu" ⇒ chia 100
            double amount = Double.parseDouble(vnp_Params.get("vnp_Amount")) / 100.0;
            System.out.println("[AMOUNT] " + amount);

            Bill bill = new Bill();
            bill.setUserId(userId);
            bill.setTotalAmount(BigDecimal.valueOf(amount));
            bill.setCurrency("VND");
            bill.setPaymentMethod("VNPAY");
            bill.setPaymentStatus("PAID");
            bill.setCreatedAt(new Timestamp(System.currentTimeMillis()));

            BillDAO billDAO = new BillDAO();
            int billId = billDAO.insertBillAndReturnId(bill);

            if (billId <= 0) {
                resp.getWriter().println("⚠️ Thanh toán thành công nhưng tạo Bill thất bại!");
                System.out.println("⚠️ Payment OK but insert Bill failed.");
                return;
            }

            // ===== 7. Tạo Ticket (QR tạm = PENDING_QR) =====
            Ticket ticket = new Ticket();
            ticket.setEventId(eventId);
            ticket.setUserId(userId);
            ticket.setCategoryTicketId(categoryTicketId);
            ticket.setBillId(billId);
            ticket.setSeatId(seatId);
            ticket.setStatus("BOOKED");
            ticket.setQrIssuedAt(new Timestamp(System.currentTimeMillis()));
            ticket.setCheckinTime(null);

            TicketDAO ticketDAO = new TicketDAO();
            int ticketId = ticketDAO.insertTicketAndReturnId(ticket);

            if (ticketId <= 0) {
                resp.getWriter().println("⚠️ Thanh toán đã PAID nhưng tạo Ticket thất bại (có thể do trùng dữ liệu).");
                System.out.println("⚠️ Payment PAID but insert Ticket failed.");
                return;
            }

            // ===== 7.1. Tạo QR từ ticket_id (QR chỉ chứa ticket_id) =====
            String qrBase64;
            try {
                qrBase64 = QRCodeUtil.generateTicketQrBase64(ticketId, 300, 300);
            } catch (Exception ex) {
                ex.printStackTrace();
                resp.getWriter().println("⚠️ Tạo QR code thất bại, vui lòng liên hệ hỗ trợ.");
                System.out.println("⚠️ Generate QR failed.");
                return;
            }

            // Cập nhật QR vào ticket
            boolean qrUpdated = ticketDAO.updateTicketQr(ticketId, qrBase64);
            if (!qrUpdated) {
                resp.getWriter().println("⚠️ Vé đã tạo nhưng cập nhật QR code thất bại. Vui lòng liên hệ hỗ trợ.");
                System.out.println("⚠️ Ticket created but update QR failed.");
                return;
            }

            // ❌ KHÔNG CẬP NHẬT Seat.status = 'INACTIVE' NỮA
            // Ghế vẫn ACTIVE để các event khác (khác ngày) có thể dùng lại cùng seat_id

            // ===== 8. Success =====
            StringBuilder sb = new StringBuilder();
            sb.append("✅ Đặt vé thành công!\n")
              .append("Ticket ID: ").append(ticketId).append("\n")
              .append("Event ID: ").append(eventId).append("\n")
              .append("Seat: ").append(seat.getSeatCode()).append("\n")
              .append("Loại vé: ").append(ct.getName()).append("\n")
              .append("Số tiền: ").append(amount).append(" VND\n")
              .append("QR (Base64 - rút gọn): ")
              .append(qrBase64 != null && qrBase64.length() > 40
                      ? qrBase64.substring(0, 40) + "..."
                      : qrBase64);

            resp.getWriter().println(sb.toString());

            System.out.println("✅ BuyTicket success. ticketId=" + ticketId);

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().println("⚠️ Lỗi xử lý VNPay / tạo vé: " + e.getMessage());
            System.out.println("❌ Exception in BuyTicketController: " + e.getMessage());
        }
    }

    private Map<String, String> parseOrderInfo(String orderInfo) {
        Map<String, String> map = new HashMap<>();
        if (orderInfo == null || orderInfo.isEmpty()) {
            return map;
        }
        String[] pairs = orderInfo.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }
}
