package controller;

import config.VnPayConfig;
import config.VnPayUtil;

import DAO.BillDAO;
import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.SeatDAO;
import DAO.TicketDAO;
import DAO.UsersDAO;
import DAO.VenueAreaDAO;
import DAO.VenueDAO;

import DTO.Bill;
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;
import DTO.Ticket;
import DTO.Venue;
import DTO.VenueArea;
import DTO.Users;

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
import java.text.SimpleDateFormat;
import java.util.*;

import utils.QRCodeUtil;
import utils.EmailUtils;

@WebServlet("/api/buyTicket")
public class BuyTicketController extends HttpServlet {

    // URL FE – chỉnh lại cho đúng môi trường của bạn (vd: http://localhost:5173 nếu dùng Vite)
    private static final String FRONTEND_BASE_URL = "http://localhost:3000";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");

        try {
            System.out.println("===== [BuyTicketController] VNPay return =====");

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

            // ===== 2. Verify chữ ký =====
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
            if (!signValue.equals(vnp_SecureHash)) {
                System.out.println("❌ Chữ ký VNPay không hợp lệ!");
                redirectToResult(resp, "failed", "invalid_signature", null);
                return;
            }

            // ===== 3. Kiểm tra mã phản hồi =====
            String responseCode = vnp_Params.get("vnp_ResponseCode");
            if (!"00".equals(responseCode)) {
                System.out.println("❌ Thanh toán thất bại! Mã lỗi: " + responseCode);
                redirectToResult(resp, "failed", responseCode, null);
                return;
            }

            // ===== 4. Parse OrderInfo =====
            String orderInfoRaw = vnp_Params.get("vnp_OrderInfo");
            String orderInfo = URLDecoder.decode(orderInfoRaw, StandardCharsets.UTF_8.toString());
            Map<String, String> infoMap = parseOrderInfo(orderInfo);

            int userId           = Integer.parseInt(infoMap.get("userId"));
            int eventId          = Integer.parseInt(infoMap.get("eventId"));
            int categoryTicketId = Integer.parseInt(infoMap.get("categoryTicketId"));
            int seatId           = Integer.parseInt(infoMap.get("seatId"));

            // ===== 5. Validate dữ liệu =====
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);
            CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
            CategoryTicket ct = categoryDAO.getActiveCategoryTicketById(categoryTicketId);
            SeatDAO seatDAO = new SeatDAO();
            Seat seat = seatDAO.getSeatById(seatId);

            if (event == null || ct == null || seat == null) {
                System.out.println("⚠️ Dữ liệu vé không hợp lệ.");
                redirectToResult(resp, "failed", "data_invalid", null);
                return;
            }

            // ===== 6. Tạo Bill (PAID) =====
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
                System.out.println("⚠️ Lỗi tạo Bill.");
                redirectToResult(resp, "failed", "bill_failed", null);
                return;
            }

            // ===== 7. Tạo Ticket (Idempotency Check) =====
            TicketDAO ticketDAO = new TicketDAO();
            int existingTicketId = ticketDAO.getTicketId(eventId, userId, categoryTicketId);
            int ticketId;
            boolean ticketAlreadyExisted = false;

            if (existingTicketId > 0) {
                ticketId = existingTicketId;
                ticketAlreadyExisted = true;
                System.out.println("⚠️ Vé đã tồn tại (Ticket ID: " + ticketId + ").");
            } else {
                Ticket ticket = new Ticket();
                ticket.setEventId(eventId);
                ticket.setUserId(userId);
                ticket.setCategoryTicketId(categoryTicketId);
                ticket.setBillId(billId);
                ticket.setSeatId(seatId);
                ticket.setStatus("BOOKED");
                ticket.setQrIssuedAt(new Timestamp(System.currentTimeMillis()));

                ticketId = ticketDAO.insertTicketAndReturnId(ticket);
                if (ticketId <= 0) {
                    int recheck = ticketDAO.getTicketId(eventId, userId, categoryTicketId);
                    if (recheck > 0) {
                        ticketId = recheck;
                        ticketAlreadyExisted = true;
                    } else {
                        System.out.println("⚠️ Lỗi tạo vé vào database.");
                        redirectToResult(resp, "failed", "ticket_failed", null);
                        return;
                    }
                }
            }

            // ===== 7.1. Cập nhật QR Code =====
            String qrBase64 = null;
            try {
                if (ticketAlreadyExisted) {
                    Ticket existing = ticketDAO.getTicketById(ticketId);
                    if (existing != null && existing.getQrCodeValue() != null) {
                        qrBase64 = existing.getQrCodeValue();
                    }
                }
                if (qrBase64 == null) {
                    qrBase64 = QRCodeUtil.generateTicketQrBase64(ticketId, 300, 300);
                    ticketDAO.updateTicketQr(ticketId, qrBase64);
                }
            } catch (Exception ex) {
                System.err.println("QR Gen Error: " + ex.getMessage());
                // không fail giao dịch, chỉ log
            }

            // ===== 8. Gửi Email Vé Điện Tử =====
            try {
                UsersDAO usersDAO = new UsersDAO();
                Users user = usersDAO.findById(userId);
                String userEmail = user != null ? user.getEmail() : null;
                String userName = user != null && user.getFullName() != null ? user.getFullName() : "Khách hàng";
                String eventTitle = event.getTitle();

                byte[] qrBytes = QRCodeUtil.generateTicketQrPngBytes(ticketId, 300, 300);

                String startTimeString = "";
                if (event.getStartTime() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy");
                    startTimeString = sdf.format(event.getStartTime());
                }

                // --- Lấy Tên Địa Điểm & Địa Chỉ ---
                String venueName = "Đang cập nhật";
                String venueAddress = "Đang cập nhật";
                try {
                    if (event.getAreaId() != null) {
                        VenueAreaDAO vaDAO = new VenueAreaDAO();
                        VenueArea area = vaDAO.getVenueAreaById(event.getAreaId());
                        if (area != null) {
                            VenueDAO vDAO = new VenueDAO();
                            Venue venue = vDAO.getVenueById(area.getVenueId());
                            if (venue != null) {
                                venueName = venue.getVenueName();

                                // Tùy DTO Venue của bạn dùng getAddress hay getLocation
                                if (venue.getAddress() != null) {
                                    venueAddress = venue.getAddress();
                                }
                                // else if (venue.getLocation() != null) {
                                //     venueAddress = venue.getLocation();
                                // }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching venue: " + e.getMessage());
                }

                // --- Tạo Link Google Maps ---
                String mapUrl = "https://www.google.com/maps";
                try {
                    if (venueAddress != null && !"Đang cập nhật".equals(venueAddress)) {
                        mapUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                URLEncoder.encode(venueAddress, "UTF-8");
                    } else if (venueName != null && !"Đang cập nhật".equals(venueName)) {
                        mapUrl = "https://www.google.com/maps/search/?api=1&query=" +
                                URLEncoder.encode(venueName, "UTF-8");
                    }
                } catch (Exception ex) {
                    mapUrl = "https://www.google.com/maps";
                }

                if (userEmail != null) {
                    final String subject = "[FPT Event] Vé điện tử: " + eventTitle;
                    final String htmlContent =
                            "<div style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>"
                                    + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>"
                                    + "  <div style='background-color: #F57224; padding: 20px; text-align: center;'>"
                                    + "    <h2 style='color: #ffffff; margin: 0;'>VÉ ĐIỆN TỬ / E-TICKET</h2>"
                                    + "  </div>"
                                    + "  <div style='padding: 30px; color: #333333;'>"
                                    + "    <p>Xin chào <strong>" + escapeHtml(userName) + "</strong>, cảm ơn bạn đã đặt vé!</p>"
                                    + "    <p>Thanh toán thành công! Dưới đây là vé tham dự sự kiện của bạn:</p>"
                                    + "    <h1 style='color: #F57224; font-size: 24px; border-bottom: 2px solid #eee; padding-bottom: 10px; margin: 0 0 16px 0;'>"
                                    +          escapeHtml(eventTitle) + "</h1>"
                                    + "    <table style='width: 100%; margin-top: 20px; border-collapse: collapse;'>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666;'>Mã vé:</td>"
                                    + "        <td style='padding: 8px; font-weight: bold;'>#" + ticketId + "</td>"
                                    + "      </tr>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666;'>Loại vé:</td>"
                                    + "        <td style='padding: 8px; font-weight: bold;'>" + escapeHtml(ct.getName()) + "</td>"
                                    + "      </tr>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666; vertical-align: top;'>Địa điểm:</td>"
                                    + "        <td style='padding: 8px;'>"
                                    + "           <div style='font-weight: bold; color: #333; font-size: 14px;'>" + escapeHtml(venueName) + "</div>"
                                    + "           <div style='font-size: 12px; margin-top: 4px;'>"
                                    + "             <a href='" + mapUrl + "' target='_blank' style='color: #007bff; text-decoration: none;'>"
                                    +                 escapeHtml(venueAddress) + " 📍 (Xem bản đồ)"
                                    + "             </a>"
                                    + "           </div>"
                                    + "        </td>"
                                    + "      </tr>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666;'>Ghế ngồi:</td>"
                                    + "        <td style='padding: 8px; font-weight: bold; color: #F57224;'>" + escapeHtml(seat.getSeatCode()) + "</td>"
                                    + "      </tr>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666;'>Giá vé:</td>"
                                    + "        <td style='padding: 8px; font-weight: bold;'>" + String.format("%,.0f", amount) + " VND</td>"
                                    + "      </tr>"
                                    + "      <tr>"
                                    + "        <td style='padding: 8px; color: #666;'>Thời gian:</td>"
                                    + "        <td style='padding: 8px; font-weight: bold; color: #28a745;'>" + startTimeString + "</td>"
                                    + "      </tr>"
                                    + "    </table>"
                                    + "    <div style='text-align: center; margin-top: 30px; padding: 20px; background-color: #f9f9f9; border-radius: 8px;'>"
                                    + "      <p style='margin-bottom: 15px; font-size: 14px; color: #666;'>Vui lòng xuất trình mã QR này tại quầy Check-in</p>"
                                    + "      <img src='cid:ticket_qr' style='width: 200px; height: 200px; border: 2px solid #ddd; padding: 5px; background: white;' alt='Ticket QR'/>"
                                    + "    </div>"
                                    + "  </div>"
                                    + "  <div style='background-color: #333; color: #aaa; text-align: center; padding: 15px; font-size: 12px;'>"
                                    + "    © 2025 FPT Event Management. All rights reserved."
                                    + "  </div>"
                                    + "</div>"
                                    + "</div>";

                    final byte[] finalQr = qrBytes;
                    new Thread(() -> {
                        try {
                            EmailUtils.sendEmailWithImage(userEmail, subject, htmlContent, finalQr, "ticket_qr");
                        } catch (Exception e) {
                            System.err.println("[BuyTicketController] Error sending email: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (Exception e) {
                System.err.println("Email Error: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("✅ Đặt vé thành công! ticketId = " + ticketId);

            // ===== 9. Redirect về FE – THÀNH CÔNG =====
            redirectToResult(resp, "success", "OK", ticketId);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("⚠️ Lỗi hệ thống: " + e.getMessage());
            redirectToResult(resp, "failed", "exception", null);
        }
    }

    private Map<String, String> parseOrderInfo(String orderInfo) {
        Map<String, String> map = new HashMap<>();
        if (orderInfo != null) {
            for (String pair : orderInfo.split("&")) {
                String[] kv = pair.split("=");
                if (kv.length == 2) map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    // Helper redirect về FE
    // Ví dụ: /dashboard/payment/success?status=success&ticketId=123&reason=OK
    private void redirectToResult(HttpServletResponse resp,
                                  String status,
                                  String reason,
                                  Integer ticketId) throws IOException {

        StringBuilder url = new StringBuilder(
                FRONTEND_BASE_URL + "/dashboard/payment/success?status=" + status
        );

        if (ticketId != null) {
            url.append("&ticketId=").append(ticketId);
        }
        if (reason != null) {
            url.append("&reason=")
               .append(URLEncoder.encode(reason, StandardCharsets.UTF_8.toString()));
        }

        resp.sendRedirect(url.toString());
    }

    // escapeHtml cho nội dung email
    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
