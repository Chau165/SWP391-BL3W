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
import jakarta.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

import utils.QRCodeUtil; // ✅ nhớ import

@WebServlet("/api/buyTicket")
public class BuyTicketController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
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

            for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext();) {
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

            if (!signValue.equals(vnp_SecureHash)) {
                resp.getWriter().println("❌ Chữ ký VNPay không hợp lệ!");
                return;
            }

            // ===== 3. Kiểm tra mã phản hồi =====
            String responseCode = vnp_Params.get("vnp_ResponseCode");
            if (!"00".equals(responseCode)) {
                resp.getWriter().println("❌ Thanh toán thất bại! Mã lỗi: " + responseCode);
                return;
            }

            // ===== 4. Lấy thông tin orderInfo =====
            String orderInfoRaw = vnp_Params.get("vnp_OrderInfo");
            String orderInfo = URLDecoder.decode(orderInfoRaw, "UTF-8");

            Map<String, String> infoMap = parseOrderInfo(orderInfo);

            int userId = Integer.parseInt(infoMap.get("userId"));
            int eventId = Integer.parseInt(infoMap.get("eventId"));
            int categoryTicketId = Integer.parseInt(infoMap.get("categoryTicketId"));
            int seatId = Integer.parseInt(infoMap.get("seatId"));

            // ===== 5. Re-validate =====
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);
            if (event == null || !"OPEN".equalsIgnoreCase(event.getStatus())) {
                resp.getWriter().println("⚠️ Event not found or not OPEN.");
                return;
            }

            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
            CategoryTicket ct = categoryDAO.getActiveCategoryTicketById(categoryTicketId);
            if (ct == null || ct.getEventId() != eventId) {
                resp.getWriter().println("⚠️ Category ticket invalid.");
                return;
            }

            SeatDAO seatDAO = new SeatDAO();
            Seat seat = seatDAO.getSeatById(seatId);

            // ✅ ĐỔI: so sánh theo areaId thay vì venueId
            if (seat == null
                    || event.getAreaId() == null
                    || seat.getAreaId() != event.getAreaId()) {
                resp.getWriter().println("⚠️ Seat invalid (không thuộc đúng khu vực của event).");
                return;
            }

            // ✅ chỉ cho đặt ghế đang ACTIVE
            if (!"ACTIVE".equalsIgnoreCase(seat.getStatus())) {
                resp.getWriter().println("⚠️ Ghế không còn khả dụng (đã được đặt hoặc khóa).");
                return;
            }

            if (seatDAO.isSeatAlreadyBookedForEvent(eventId, seatId)) {
                resp.getWriter().println("⚠️ Seat đã được đặt cho event này.");
                return;
            }

            // ===== 6. Tạo Bill (PAID) - không có event_id trong Bill =====
            double amount = Double.parseDouble(vnp_Params.get("vnp_Amount")) / 100.0;

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
                return;
            }

            // ===== 7. Tạo Ticket (chưa có QR, chỉ tạo record) =====
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
                resp.getWriter().println("⚠️ Thanh toán đã PAID nhưng tạo Ticket thất bại!");
                return;
            }

            // ===== 7.1. Tạo QR từ ticket_id (QR chỉ chứa ticket_id) =====
            String qrBase64;
            try {
                qrBase64 = QRCodeUtil.generateTicketQrBase64(ticketId, 300, 300);
            } catch (Exception ex) {
                ex.printStackTrace();
                resp.getWriter().println("⚠️ Tạo QR code thất bại, vui lòng liên hệ hỗ trợ.");
                return;
            }

            // Cập nhật QR vào ticket
            boolean qrUpdated = ticketDAO.updateTicketQr(ticketId, qrBase64);
            if (!qrUpdated) {
                resp.getWriter().println("⚠️ Vé đã tạo nhưng cập nhật QR code thất bại. Vui lòng liên hệ hỗ trợ.");
                return;
            }

            // ===== 7.2. Cập nhật trạng thái ghế sang INACTIVE =====
            boolean seatUpdated = seatDAO.updateSeatStatus(seatId, "INACTIVE");
            if (!seatUpdated) {
                resp.getWriter().println("⚠️ Vé đã tạo nhưng cập nhật trạng thái ghế thất bại. Vui lòng liên hệ hỗ trợ.");
                return;
            }

            // ===== 8. Success =====
            resp.getWriter().println("✅ Đặt vé thành công!\n"
                    + "Ticket ID: " + ticketId + "\n"
                    + "Event ID: " + eventId + "\n"
                    + "Seat: " + seat.getSeatCode() + "\n"
                    + "Loại vé: " + ct.getName() + "\n"
                    + "Số tiền: " + amount + " VND\n"
                    + "QR (Base64 - rút gọn): " + qrBase64.substring(0, 40) + "...");

        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().println("⚠️ Lỗi xử lý VNPay / tạo vé: " + e.getMessage());
        }
    }

    private Map<String, String> parseOrderInfo(String orderInfo) {
        Map<String, String> map = new HashMap<>();
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
