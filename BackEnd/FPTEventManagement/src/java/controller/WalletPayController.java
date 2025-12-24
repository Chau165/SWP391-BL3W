// controller/WalletPayController.java
package controller;

import DAO.*;
import DTO.*;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import utils.EmailUtils;
import utils.QRCodeUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import mylib.DBUtils;

@WebServlet("/api/wallet/pay-ticket")
public class WalletPayController extends HttpServlet {

    private static final String FRONTEND_BASE_URL = "http://localhost:3000";

    // ===== DAO giống PaymentTicketController =====
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();
    private final SeatDAO seatDAO = new SeatDAO();
    private final TicketDAO ticketDAO = new TicketDAO();
    private final CategoryTicketDAO categoryDAO = new CategoryTicketDAO();
    private final EventDAO eventDAO = new EventDAO();

    // ===== Wallet + Bill =====
    private final WalletDAO walletDAO = new WalletDAO();
    private final BillDAO billDAO = new BillDAO();

    // ===== Email/QR giống BuyTicketController =====
    private final UsersDAO usersDAO = new UsersDAO();
    private final VenueAreaDAO vaDAO = new VenueAreaDAO();
    private final VenueDAO vDAO = new VenueDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType("text/plain;charset=UTF-8");

        List<Integer> createdTempTicketIds = new ArrayList<>();
        List<Integer> seatIds = new ArrayList<>();
        List<Integer> categoryIdsForSeats = new ArrayList<>();

        try {
            System.out.println("===== [WalletPayController] New request =====");
            System.out.println("QueryString: " + req.getQueryString());

            // ===== (1) Params =====
            String userIdStr = req.getParameter("userId");
            String eventIdStr = req.getParameter("eventId");
            String seatIdsStr = req.getParameter("seatIds"); // "1,2,3"

            if (isBlank(userIdStr) || isBlank(eventIdStr) || isBlank(seatIdsStr)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Missing userId/eventId/seatIds");
                return;
            }

            int userId = Integer.parseInt(userIdStr);
            int eventId = Integer.parseInt(eventIdStr);

            for (String t : seatIdsStr.split(",")) {
                if (t != null && !t.trim().isEmpty()) {
                    seatIds.add(Integer.parseInt(t.trim()));
                }
            }
            if (seatIds.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("seatIds is empty");
                return;
            }

            // ===== (2) Validate Event OPEN =====
            Event event = eventDAO.getEventById(eventId);
            if (event == null || !"OPEN".equalsIgnoreCase(event.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().println("Event not found or not OPEN");
                return;
            }

            // ===== (3) Validate seats + map seatType -> CategoryTicket + tính tiền =====
            BigDecimal totalPrice = BigDecimal.ZERO;

            for (Integer seatId : seatIds) {
                Seat seat = eventSeatLayoutDAO.getSeatForEvent(eventId, seatId);

                if (seat == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " not configured for this event");
                    return;
                }

                if (event.getAreaId() != null && seat.getAreaId() != event.getAreaId()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " does not belong to event area");
                    return;
                }

                if (!"AVAILABLE".equalsIgnoreCase(seat.getStatus())) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " is not AVAILABLE for this event");
                    return;
                }

                String seatType = seat.getSeatType();
                if (isBlank(seatType)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Seat " + seatId + " has no seatType");
                    return;
                }

                CategoryTicket ctByType = categoryDAO.getActiveCategoryTicketByEventIdAndName(eventId, seatType);
                if (ctByType == null) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("No active category ticket for seatType: " + seatType);
                    return;
                }

                categoryIdsForSeats.add(ctByType.getCategoryTicketId());
                totalPrice = totalPrice.add(ctByType.getPrice());
            }

            // ===== (4) Check booked/reserved trước (pre-check) =====
            try {
                List<Integer> alreadyBookedSeatIds = seatDAO.findAlreadyBookedSeatIdsForEvent(eventId, seatIds);
                if (!alreadyBookedSeatIds.isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().println("Some seats already reserved/booked: " + alreadyBookedSeatIds);
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().println("Error checking booked seats: " + ex.toString());
                return;
            }

            // ===== (5) TRANSACTION: insert PENDING -> deduct wallet -> bill -> BOOKED =====
            Timestamp now = new Timestamp(System.currentTimeMillis());
            int billId;

            try ( Connection conn = DBUtils.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // (5.1) Insert PENDING tickets (hold seats)
                    for (int i = 0; i < seatIds.size(); i++) {
                        Integer seatId = seatIds.get(i);
                        Integer categoryIdForSeat = categoryIdsForSeats.get(i);

                        Ticket temp = new Ticket();
                        temp.setEventId(eventId);
                        temp.setUserId(userId);
                        temp.setCategoryTicketId(categoryIdForSeat);
                        temp.setSeatId(seatId);
                        temp.setBillId(null);

                        temp.setStatus("PENDING");

                        // ✅ GIỐNG PAYMENT: không cho NULL vì DB NOT NULL
                        temp.setQrCodeValue("PENDING_QR");   // hoặc "PENDING_QR" / "PENDING"
                        temp.setQrIssuedAt(now);             // hoặc new Timestamp(System.currentTimeMillis())

                        temp.setCheckinTime(null); // nếu Ticket có field này

                        int tid = ticketDAO.insertTicketAndReturnId(conn, temp);
                        if (tid <= 0) {
                            throw new SQLException("insertTicketAndReturnId <= 0 for seatId=" + seatId);
                        }
                        createdTempTicketIds.add(tid);
                    }

                    // (5.2) Lock wallet + check đủ tiền + trừ tiền
                    BigDecimal wallet = walletDAO.getWalletForUpdate(conn, userId);
                    if (wallet == null) {
                        // rollback + trả ghế
                        ticketDAO.deleteTicketsByIds(conn, createdTempTicketIds);
                        conn.rollback();
                        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        resp.getWriter().println("user_not_found");
                        return;
                    }

                    if (wallet.compareTo(totalPrice) < 0) {
                        ticketDAO.deleteTicketsByIds(conn, createdTempTicketIds);
                        conn.rollback();
                        resp.setStatus(402);
                        resp.getWriter().println("wallet_not_enough");
                        return;
                    }

                    boolean deducted = walletDAO.deductWallet(conn, userId, totalPrice);
                    if (!deducted) {
                        ticketDAO.deleteTicketsByIds(conn, createdTempTicketIds);
                        conn.rollback();
                        resp.setStatus(402);
                        resp.getWriter().println("wallet_not_enough");
                        return;
                    }

                    // (5.3) Create Bill PAID
                    Bill bill = new Bill();
                    bill.setUserId(userId);
                    bill.setTotalAmount(totalPrice);
                    bill.setCurrency("VND");
                    bill.setPaymentMethod("WALLET");
                    bill.setPaymentStatus("PAID");
                    bill.setCreatedAt(now);

                    billId = billDAO.insertBillAndReturnId(conn, bill);
                    if (billId <= 0) {
                        ticketDAO.deleteTicketsByIds(conn, createdTempTicketIds);
                        conn.rollback();
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        resp.getWriter().println("bill_failed");
                        return;
                    }

                    // (5.4) Update tickets -> BOOKED
                    for (Integer tid : createdTempTicketIds) {
                        Ticket t = new Ticket();
                        t.setTicketId(tid);
                        t.setBillId(billId);
                        t.setStatus("BOOKED");
                        t.setQrIssuedAt(now);

                        ticketDAO.updateTicketAfterPayment(conn, t);
                    }

                    conn.commit();

                } catch (SQLException ex) {
                    // Nếu insert PENDING fail do tranh ghế (unique constraint) => 409
                    ex.printStackTrace();
                    try {
                        conn.rollback();
                    } catch (Exception ignore) {
                    }

                    resp.setStatus(HttpServletResponse.SC_CONFLICT);
                    resp.getWriter().println("Seat(s) already taken by another user. Please choose other seats.");
                    return;

                } catch (Exception ex) {
                    ex.printStackTrace();
                    try {
                        // rollback + best-effort clean PENDING created in this tx
                        ticketDAO.deleteTicketsByIds(conn, createdTempTicketIds);
                        conn.rollback();
                    } catch (Exception ignore) {
                    }

                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    resp.getWriter().println("wallet_payment_exception: " + ex.getMessage());
                    return;
                }
            }

            // ===== (6) Sau commit: QR + Email (copy BuyTicketController) =====
            try {
                // Update QR per ticket (optional)
                for (Integer tid : createdTempTicketIds) {
                    try {
                        String qrBase64 = QRCodeUtil.generateTicketQrBase64(tid, 300, 300);
                        ticketDAO.updateTicketQr(tid, qrBase64);
                    } catch (Exception ex) {
                        System.err.println("QR Gen Error for ticketId " + tid + ": " + ex.getMessage());
                    }
                }

                Users user = usersDAO.findById(userId);

                if (user != null && user.getEmail() != null) {
                    // seatCodes
                    List<Seat> seats = new ArrayList<>();
                    for (Integer sId : seatIds) {
                        Seat s = seatDAO.getSeatById(sId);
                        if (s != null) {
                            seats.add(s);
                        }
                    }

                    String seatCodes = seats.stream().map(Seat::getSeatCode).collect(Collectors.joining(", "));
                    String ticketIdsStringForEmail = createdTempTicketIds.stream().map(String::valueOf).collect(Collectors.joining(", "));

                    // ticketTypes display (hiển thị loại vé, bằng cách phân loại gửi về mail)
                    Map<Integer, Long> catCount = categoryIdsForSeats.stream()
                            .collect(Collectors.groupingBy(x -> x, Collectors.counting()));

                    List<String> catParts = new ArrayList<>();
                    for (Map.Entry<Integer, Long> e : catCount.entrySet()) {
                        CategoryTicket c = categoryDAO.getActiveCategoryTicketById(e.getKey());
                        String name = (c != null && c.getName() != null) ? c.getName() : ("Category#" + e.getKey());
                        catParts.add(name + " x" + e.getValue());
                    }
                    Collections.sort(catParts);
                    String ticketTypesForEmail = String.join(", ", catParts);

                    // gộp QR (mã qr để gủi về mail, nếu mua nhiều sẽ lấy hết vé để gộp vào qr luôn)
                    String qrContent = (createdTempTicketIds.size() == 1)
                            ? String.valueOf(createdTempTicketIds.get(0))
                            : "TICKETS:" + createdTempTicketIds.stream().map(String::valueOf).collect(Collectors.joining(","));

                    byte[] qrBytes = QRCodeUtil.generateQRCodePngBytes(qrContent, 300, 300);

                    String startTimeString = "";
                    if (event.getStartTime() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy");
                        startTimeString = sdf.format(event.getStartTime());
                    }

                    // venue info (optional)
                    String venueName = "Đang cập nhật";
                    String venueAddress = "Đang cập nhật";
                    try {
                        if (event.getAreaId() != null) {
                            VenueArea area = vaDAO.getVenueAreaById(event.getAreaId());
                            if (area != null) {
                                Venue venue = vDAO.getVenueById(area.getVenueId());
                                if (venue != null) {
                                    venueName = venue.getVenueName();
                                    if (venue.getAddress() != null) {
                                        venueAddress = venue.getAddress();
                                    }
                                }
                            }
                        }
                    } catch (Exception ignore) {
                    }

                    String mapUrl = "https://www.google.com/maps";

                    final String subject = "[FPT Event] Vé điện tử: " + event.getTitle();

                    final String htmlContent
                            = "<div style='font-family: Arial, sans-serif; background-color: #f4f4f4; padding: 20px;'>"
                            + "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden;'>"
                            + "<div style='background-color: #F57224; padding: 20px; text-align: center;'>"
                            + "<h2 style='color: #ffffff; margin: 0;'>VÉ ĐIỆN TỬ / E-TICKET</h2></div>"
                            + "<div style='padding: 30px; color: #333333;'>"
                            + "<p>Xin chào <strong>" + escapeHtml(user.getFullName() != null ? user.getFullName() : "Khách hàng") + "</strong>, cảm ơn bạn đã đặt vé!</p>"
                            + "<p>Thanh toán bằng <strong>WALLET</strong> thành công!</p>"
                            + "<h1 style='color: #F57224; font-size: 24px; border-bottom: 2px solid #eee; padding-bottom: 10px; margin: 0 0 16px 0;'>"
                            + escapeHtml(event.getTitle()) + "</h1>"
                            + "<table style='width: 100%; margin-top: 20px; border-collapse: collapse;'>"
                            + "<tr><td style='padding: 8px; color: #666;'>Mã vé:</td><td style='padding: 8px; font-weight: bold;'>#"
                            + escapeHtml(ticketIdsStringForEmail) + "</td></tr>"
                            + "<tr><td style='padding: 8px; color: #666;'>Loại vé:</td><td style='padding: 8px; font-weight: bold;'>"
                            + escapeHtml(ticketTypesForEmail) + "</td></tr>"
                            + "<tr><td style='padding: 8px; color: #666; vertical-align: top;'>Địa điểm:</td><td style='padding: 8px;'>"
                            + "<div style='font-weight: bold;'>" + escapeHtml(venueName) + "</div>"
                            + "<div style='font-size: 12px; margin-top: 4px;'><a href='" + mapUrl + "' target='_blank'>"
                            + escapeHtml(venueAddress) + " 📍 (Xem bản đồ)</a></div></td></tr>"
                            + "<tr><td style='padding: 8px; color: #666;'>Ghế ngồi:</td><td style='padding: 8px; font-weight: bold; color: #F57224;'>"
                            + escapeHtml(seatCodes) + "</td></tr>"
                            + "<tr><td style='padding: 8px; color: #666;'>Tổng tiền:</td><td style='padding: 8px; font-weight: bold;'>"
                            + String.format("%,.0f", totalPrice.doubleValue()) + " VND</td></tr>"
                            + "<tr><td style='padding: 8px; color: #666;'>Thời gian:</td><td style='padding: 8px; font-weight: bold; color: #28a745;'>"
                            + startTimeString + "</td></tr>"
                            + "</table>"
                            + "<div style='text-align: center; margin-top: 30px; padding: 20px; background-color: #f9f9f9; border-radius: 8px;'>"
                            + "<p style='margin-bottom: 15px; font-size: 14px; color: #666;'>Vui lòng xuất trình mã QR này tại quầy Check-in</p>"
                            + "<img src='cid:ticket_qr' style='width: 200px; height: 200px;' alt='Ticket QR'/>"
                            + "</div></div></div></div>";

                    new Thread(() -> {
                        try {
                            EmailUtils.sendEmailWithImage(user.getEmail(), subject, htmlContent, qrBytes, "ticket_qr");
                        } catch (Exception e) {
                            System.err.println("[WalletPayController] Error sending email: " + e.getMessage());
                        }
                    }).start();
                }

            } catch (Exception e) {
                System.err.println("Email/QR Error: " + e.getMessage());
            }

            // ===== (7) Redirect FE success =====
            // ===== (7) Redirect FE success =====
            String ticketIdsString = createdTempTicketIds.stream().map(String::valueOf).collect(Collectors.joining(","));

// get new wallet for redirect (quick demo)
            //Biến newWallet để ghi lại số tiền trong ví hiện tại của user là nhiêu để trả về cho bên FE hiển thị lại
            BigDecimal newWallet = null;
            try ( Connection c2 = DBUtils.getConnection()) {
                newWallet = walletDAO.getWalletByUserId(c2, userId); // bạn cần DAO method này (bên dưới)
            } catch (Exception ignore) {
            }

            redirectToResult(resp, "success", "OK", ticketIdsString, newWallet);

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().println("exception: " + e.getMessage());
        }
    }

    private void redirectToResult(HttpServletResponse resp, String status, String reason, String ticketIds, BigDecimal newWallet) throws IOException {
        StringBuilder url = new StringBuilder(
                FRONTEND_BASE_URL + "/dashboard/payment/success?status=" + status + "&method=wallet"
        );

        if (ticketIds != null) {
            url.append("&ticketIds=").append(URLEncoder.encode(ticketIds, StandardCharsets.UTF_8.toString()));
        }
        if (reason != null) {
            url.append("&reason=").append(URLEncoder.encode(reason, StandardCharsets.UTF_8.toString()));
        }
        if (newWallet != null) {
            url.append("&newWallet=").append(URLEncoder.encode(newWallet.toPlainString(), StandardCharsets.UTF_8.toString()));
        }
        resp.sendRedirect(url.toString());
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
