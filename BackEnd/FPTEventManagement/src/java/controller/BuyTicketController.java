// controller/BuyTicketController.java
package controller;

// ==== Import config: thông tin VNPay + tiện ích ký HMAC ====
import config.VnPayConfig;
import config.VnPayUtil;

// ==== Import DAO: lớp làm việc với database ====
import DAO.BillDAO;
import DAO.CategoryTicketDAO;
import DAO.EventDAO;
import DAO.SeatDAO;
import DAO.TicketDAO;
import DAO.UsersDAO;
import DAO.VenueAreaDAO;
import DAO.VenueDAO;

// ==== Import DTO: đối tượng dữ liệu (mapping từ DB ra Java object) ====
import DTO.Bill;
import DTO.CategoryTicket;
import DTO.Event;
import DTO.Seat;
import DTO.Ticket;
import DTO.Venue;
import DTO.VenueArea;
import DTO.Users;

// ==== Servlet API ====
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// ==== Java core ====
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

// ==== Utils: tạo QR và gửi email ====
import utils.QRCodeUtil;
import utils.EmailUtils;

@WebServlet("/api/buyTicket")
public class BuyTicketController extends HttpServlet {

    private static final String FRONTEND_BASE_URL = "http://localhost:3000";
    private final TicketDAO ticketDAO = new TicketDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("text/plain;charset=UTF-8");

        List<Integer> tempTicketIds = null;

        try {
            System.out.println("===== [BuyTicketController] VNPay return =====");

            // =========================================================
            // (1) Lấy toàn bộ params VNPay gửi về (vnp_*)
            // =========================================================
            Map<String, String> vnp_Params = new HashMap<>();
            Map<String, String[]> paramMap = req.getParameterMap();

            for (String key : paramMap.keySet()) {
                String[] values = paramMap.get(key);
                if (values != null && values.length > 0) {
                    vnp_Params.put(key, values[0]);
                }
            }

            // =========================================================
            // (2) Tách secure hash ra để verify chữ ký
            // =========================================================
            String vnp_SecureHash = vnp_Params.get("vnp_SecureHash");

            vnp_Params.remove("vnp_SecureHash");
            vnp_Params.remove("vnp_SecureHashType");

            // =========================================================
            // (3) Verify chữ ký: sort field + build hashData + HMAC SHA512
            // =========================================================
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
                System.out.println("❌ Chữ ký VNPay không hợp lệ!");
                redirectToResult(resp, "failed", "invalid_signature", null);
                return;
            }

            // =========================================================
            // (4) Check mã phản hồi VNPay: vnp_ResponseCode
            // =========================================================
            String responseCode = vnp_Params.get("vnp_ResponseCode");

            if (!"00".equals(responseCode)) {
                System.out.println("❌ Thanh toán thất bại! Mã lỗi: " + responseCode);
                redirectToResult(resp, "failed", responseCode, null);
                return;
            }

            // =========================================================
            // (5) Parse vnp_OrderInfo
            // =========================================================
            String orderInfoRaw = vnp_Params.get("vnp_OrderInfo");
            String orderInfo = URLDecoder.decode(orderInfoRaw, StandardCharsets.UTF_8.toString());
            Map<String, String> infoMap = parseOrderInfo(orderInfo);

            // Bắt buộc phải có userId, eventId
            if (isBlank(infoMap.get("userId")) || isBlank(infoMap.get("eventId"))) {
                System.out.println("⚠️ userId/eventId missing in orderInfo");
                redirectToResult(resp, "failed", "order_info_missing", null);
                return;
            }

            int userId = Integer.parseInt(infoMap.get("userId"));
            int eventId = Integer.parseInt(infoMap.get("eventId"));

            // =========================================================
            // (6) seatIds
            // =========================================================
            String seatIdsStr = infoMap.get("seatIds");
            if (isBlank(seatIdsStr)) {
                System.out.println("⚠️ seatIds missing in orderInfo");
                redirectToResult(resp, "failed", "seatIds_missing", null);
                return;
            }

            List<Integer> seatIds = Arrays.stream(seatIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            if (seatIds.isEmpty()) {
                System.out.println("⚠️ seatIds empty after parse");
                redirectToResult(resp, "failed", "seatIds_empty", null);
                return;
            }

            // =========================================================
            // (7) tempTicketIds
            // =========================================================
            String tempTicketIdsStr = infoMap.get("tempTicketIds");
            if (isBlank(tempTicketIdsStr)) {
                System.out.println("⚠️ tempTicketIds missing in orderInfo");
                redirectToResult(resp, "failed", "tempTicketIds_missing", null);
                return;
            }

            tempTicketIds = Arrays.stream(tempTicketIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            if (tempTicketIds.isEmpty()) {
                System.out.println("⚠️ tempTicketIds empty after parse");
                redirectToResult(resp, "failed", "tempTicketIds_empty", null);
                return;
            }

            // =========================================================
            // (7.5) categoryTicketIdsUsed (quan trọng cho multi-seat/multi-type)
            // =========================================================
            String categoryTicketIdsUsedStr = infoMap.get("categoryTicketIdsUsed");

            // Nếu hệ thống bạn chắc chắn luôn gửi field này từ PaymentTicketController thì có thể coi là bắt buộc.
            // Ở đây mình cho phép null/empty để backward compatible, nhưng nếu null thì sẽ chỉ validate theo ticket trong DB.
            Set<Integer> categoryIdsUsed = new HashSet<>();
            if (!isBlank(categoryTicketIdsUsedStr)) {
                categoryIdsUsed = Arrays.stream(categoryTicketIdsUsedStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Integer::parseInt)
                        .collect(Collectors.toSet());
            }

            // =========================================================
            // (8) Validate dữ liệu: Event, Seats, Tickets PENDING
            // =========================================================
            EventDAO eventDAO = new EventDAO();
            Event event = eventDAO.getEventById(eventId);

            // Nếu event không hợp lệ => xoá ticket PENDING để trả ghế
            if (event == null) {
                System.out.println("⚠️ Event không hợp lệ (event null).");
                ticketDAO.deleteTicketsByIds(tempTicketIds);
                redirectToResult(resp, "failed", "event_invalid", null);
                return;
            }

            SeatDAO seatDAO = new SeatDAO();

            // Lấy Seat objects để lấy seatCode đưa vào email
            List<Seat> seats = new ArrayList<>();
            for (Integer sId : seatIds) {
                Seat s = seatDAO.getSeatById(sId);
                if (s == null) {
                    System.out.println("⚠️ seatId " + sId + " not found");
                    ticketDAO.deleteTicketsByIds(tempTicketIds);
                    redirectToResult(resp, "failed", "seat_not_found", null);
                    return;
                }
                seats.add(s);
            }

            // Lấy list Ticket PENDING theo tempTicketIds
            List<Ticket> pendingTickets = ticketDAO.findTicketsByIds(tempTicketIds);

            if (pendingTickets.size() != tempTicketIds.size()) {
                System.out.println("⚠️ Không tìm đủ ticket PENDING tương ứng tempTicketIds");
                ticketDAO.deleteTicketsByIds(tempTicketIds);
                redirectToResult(resp, "failed", "pending_tickets_missing", null);
                return;
            }

            // ---- Validate ticket match order: userId/eventId + status PENDING + category hợp lệ ----
            for (Ticket t : pendingTickets) {
                if (t.getEventId() != eventId || t.getUserId() != userId) {
                    System.out.println("⚠️ Ticket " + t.getTicketId() + " không khớp user/event.");
                    ticketDAO.deleteTicketsByIds(tempTicketIds);
                    redirectToResult(resp, "failed", "pending_ticket_mismatch", null);
                    return;
                }

                if (!"PENDING".equalsIgnoreCase(t.getStatus())) {
                    System.out.println("⚠️ Ticket " + t.getTicketId() + " không còn ở trạng thái PENDING.");
                    ticketDAO.deleteTicketsByIds(tempTicketIds);
                    redirectToResult(resp, "failed", "pending_ticket_invalid_status", null);
                    return;
                }

                // Nếu có gửi categoryTicketIdsUsed => validate ticket.categoryTicketId phải nằm trong đó
                if (!categoryIdsUsed.isEmpty() && !categoryIdsUsed.contains(t.getCategoryTicketId())) {
                    System.out.println("⚠️ Ticket " + t.getTicketId()
                            + " categoryTicketId=" + t.getCategoryTicketId()
                            + " không nằm trong categoryTicketIdsUsed=" + categoryIdsUsed);
                    ticketDAO.deleteTicketsByIds(tempTicketIds);
                    redirectToResult(resp, "failed", "pending_ticket_category_invalid", null);
                    return;
                }
            }

            // =========================================================
            // (9) Tạo Bill trạng thái PAID
            // =========================================================
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
                ticketDAO.deleteTicketsByIds(tempTicketIds);
                redirectToResult(resp, "failed", "bill_failed", null);
                return;
            }

            // =========================================================
            // (10) Update Ticket: PENDING -> BOOKED + set billId + set qrIssuedAt
            // =========================================================
            Timestamp now = new Timestamp(System.currentTimeMillis());
            List<Integer> finalTicketIds = new ArrayList<>();

            for (Ticket t : pendingTickets) {
                try {
                    t.setBillId(billId);
                    t.setStatus("BOOKED");
                    t.setQrIssuedAt(now);

                    ticketDAO.updateTicketAfterPayment(t);

                    int tid = t.getTicketId();
                    finalTicketIds.add(tid);

                    try {
                        String qrBase64 = QRCodeUtil.generateTicketQrBase64(tid, 300, 300);
                        ticketDAO.updateTicketQr(tid, qrBase64);
                    } catch (Exception ex) {
                        System.err.println("QR Gen Error for ticketId " + tid + ": " + ex.getMessage());
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();

                    if (isUniqueConstraintViolation(ex)) {
                        System.out.println("❌ Unique violation khi update ticketId=" + t.getTicketId());
                        ticketDAO.deleteTicketsByIds(tempTicketIds);
                        redirectToResult(resp, "failed", "seat_already_booked", null);
                    } else {
                        System.out.println("⚠️ DB error when updating ticket: " + ex.toString());
                        ticketDAO.deleteTicketsByIds(tempTicketIds);
                        redirectToResult(resp, "failed", "ticket_failed_db", null);
                    }
                    return;
                }
            }

            if (finalTicketIds.isEmpty()) {
                System.out.println("⚠️ Không update được vé nào.");
                ticketDAO.deleteTicketsByIds(tempTicketIds);
                redirectToResult(resp, "failed", "ticket_failed", null);
                return;
            }

            // =========================================================
            // (11) Gửi Email vé điện tử (gộp nhiều vé)
            // =========================================================
            try {
                UsersDAO usersDAO = new UsersDAO();
                Users user = usersDAO.findById(userId);

                String userEmail = user != null ? user.getEmail() : null;
                String userName = (user != null && user.getFullName() != null) ? user.getFullName() : "Khách hàng";
                String eventTitle = event.getTitle();

                String seatCodes = seats.stream()
                        .map(Seat::getSeatCode)
                        .collect(Collectors.joining(", "));

                String ticketIdsStringForEmail = finalTicketIds.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));

                // ✅ Show loại vé đúng cho multi-type: group categoryTicketId -> count -> name
                CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
                Map<Integer, Long> catCount = pendingTickets.stream()
                        .collect(Collectors.groupingBy(Ticket::getCategoryTicketId, Collectors.counting()));

                List<String> catParts = new ArrayList<>();
                for (Map.Entry<Integer, Long> e : catCount.entrySet()) {
                    CategoryTicket c = categoryDAO.getActiveCategoryTicketById(e.getKey());
                    String name = (c != null && c.getName() != null) ? c.getName() : ("Category#" + e.getKey());
                    catParts.add(name + " x" + e.getValue());
                }
                // Sort để hiển thị ổn định
                Collections.sort(catParts);
                String ticketTypesForEmail = String.join(", ", catParts);

                // Gộp nhiều vé thành 1 QR
                String qrContent;
                if (finalTicketIds.size() == 1) {
                    qrContent = String.valueOf(finalTicketIds.get(0));
                } else {
                    qrContent = "TICKETS:" + finalTicketIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(","));
                }

                byte[] qrBytes = QRCodeUtil.generateQRCodePngBytes(qrContent, 300, 300);

                String startTimeString = "";
                if (event.getStartTime() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy");
                    startTimeString = sdf.format(event.getStartTime());
                }

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
                                if (venue.getAddress() != null) {
                                    venueAddress = venue.getAddress();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching venue: " + e.getMessage());
                }

                String mapUrl = "https://www.google.com/maps";
                try {
                    if (venueAddress != null && !"Đang cập nhật".equals(venueAddress)) {
                        mapUrl = "https://www.google.com/maps/search/?api=1&query="
                                + URLEncoder.encode(venueAddress, "UTF-8");
                    } else if (venueName != null && !"Đang cập nhật".equals(venueName)) {
                        mapUrl = "https://www.google.com/maps/search/?api=1&query="
                                + URLEncoder.encode(venueName, "UTF-8");
                    }
                } catch (Exception ex) {
                    mapUrl = "https://www.google.com/maps";
                }

                if (userEmail != null) {
                    final String subject = "[FPT Event] Vé điện tử: " + eventTitle;

                    final String htmlContent
                            = "<div style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>"
                            + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 8px rgba(0,0,0,0.1);'>"
                            + " <div style='background-color: #F57224; padding: 20px; text-align: center;'>"
                            + " <h2 style='color: #ffffff; margin: 0;'>VÉ ĐIỆN TỬ / E-TICKET</h2>"
                            + " </div>"
                            + " <div style='padding: 30px; color: #333333;'>"
                            + " <p>Xin chào <strong>" + escapeHtml(userName) + "</strong>, cảm ơn bạn đã đặt vé!</p>"
                            + " <p>Thanh toán thành công! Dưới đây là vé tham dự sự kiện của bạn:</p>"
                            + " <h1 style='color: #F57224; font-size: 24px; border-bottom: 2px solid #eee; padding-bottom: 10px; margin: 0 0 16px 0;'>"
                            + escapeHtml(eventTitle) + "</h1>"
                            + " <table style='width: 100%; margin-top: 20px; border-collapse: collapse;'>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666;'>Mã vé:</td>"
                            + " <td style='padding: 8px; font-weight: bold;'>#" + escapeHtml(ticketIdsStringForEmail) + "</td>"
                            + " </tr>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666;'>Loại vé:</td>"
                            + " <td style='padding: 8px; font-weight: bold;'>" + escapeHtml(ticketTypesForEmail) + "</td>"
                            + " </tr>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666; vertical-align: top;'>Địa điểm:</td>"
                            + " <td style='padding: 8px;'>"
                            + " <div style='font-weight: bold; color: #333; font-size: 14px;'>" + escapeHtml(venueName) + "</div>"
                            + " <div style='font-size: 12px; margin-top: 4px;'>"
                            + " <a href='" + mapUrl + "' target='_blank' style='color: #007bff; text-decoration: none;'>"
                            + escapeHtml(venueAddress) + " 📍 (Xem bản đồ)"
                            + " </a>"
                            + " </div>"
                            + " </td>"
                            + " </tr>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666;'>Ghế ngồi:</td>"
                            + " <td style='padding: 8px; font-weight: bold; color: #F57224;'>" + escapeHtml(seatCodes) + "</td>"
                            + " </tr>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666;'>Tổng tiền:</td>"
                            + " <td style='padding: 8px; font-weight: bold;'>" + String.format("%,.0f", amount) + " VND</td>"
                            + " </tr>"
                            + " <tr>"
                            + " <td style='padding: 8px; color: #666;'>Thời gian:</td>"
                            + " <td style='padding: 8px; font-weight: bold; color: #28a745;'>" + startTimeString + "</td>"
                            + " </tr>"
                            + " </table>"
                            + " <div style='text-align: center; margin-top: 30px; padding: 20px; background-color: #f9f9f9; border-radius: 8px;'>"
                            + " <p style='margin-bottom: 15px; font-size: 14px; color: #666;'>Vui lòng xuất trình mã QR này tại quầy Check-in</p>"
                            + " <img src='cid:ticket_qr' style='width: 200px; height: 200px; border: 2px solid #ddd; padding: 5px; background: white;' alt='Ticket QR'/>"
                            + " </div>"
                            + " </div>"
                            + " <div style='background-color: #333; color: #aaa; text-align: center; padding: 15px; font-size: 12px;'>"
                            + " © 2025 FPT Event Management. All rights reserved."
                            + " </div>"
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

            // =========================================================
            // (12) Redirect về FE: success
            // =========================================================
            // Bạn có thể trả finalTicketIds thay vì tempTicketIds nếu muốn:
            // String ticketIdsString = finalTicketIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String ticketIdsString = tempTicketIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            System.out.println("✅ Đặt vé thành công! ticketIds = " + ticketIdsString);
            redirectToResult(resp, "success", "OK", ticketIdsString);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("⚠️ Lỗi hệ thống: " + e.getMessage());

            if (tempTicketIds != null && !tempTicketIds.isEmpty()) {
                try {
                    ticketDAO.deleteTicketsByIds(tempTicketIds);
                } catch (Exception ex2) {
                    ex2.printStackTrace();
                }
            }

            redirectToResult(resp, "failed", "exception", null);
        }
    }

    /**
     * parseOrderInfo: Input dạng:
     * "userId=1&eventId=2&categoryTicketId=3&seatIds=1,2&tempTicketIds=10,11&categoryTicketIdsUsed=3,4"
     * Output: Map để lấy từng field.
     *
     * ✅ FIX: split("=", 2) để không vỡ nếu value có dấu '='
     */
    private Map<String, String> parseOrderInfo(String orderInfo) {
        Map<String, String> map = new HashMap<>();
        if (orderInfo != null) {
            for (String pair : orderInfo.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    map.put(kv[0], kv[1]);
                }
            }
        }
        return map;
    }

    private void redirectToResult(HttpServletResponse resp,
            String status,
            String reason,
            String ticketIds) throws IOException {

        StringBuilder url = new StringBuilder(
                FRONTEND_BASE_URL + "/dashboard/payment/success?status=" + status
        );

        if (ticketIds != null) {
            url.append("&ticketIds=")
                    .append(URLEncoder.encode(ticketIds, StandardCharsets.UTF_8.toString()));
        }

        if (reason != null) {
            url.append("&reason=")
                    .append(URLEncoder.encode(reason, StandardCharsets.UTF_8.toString()));
        }

        resp.sendRedirect(url.toString());
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isUniqueConstraintViolation(SQLException ex) {
        int code = ex.getErrorCode();
        return code == 2627 || code == 2601;
    }
}
