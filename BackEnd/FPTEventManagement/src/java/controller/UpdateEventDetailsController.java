package controller;
//Controller này là để update cho cả sự kiện chưa mở và sự kiện đã mở. Nếu chưa mở thì trạng thái là CLOSED cho phép Cập nhật hết. 
//Nếu status là OPEN thì chỉ cho phép cập nhật trừ số lượng ghế đã set từ đầu.
import DAO.EventDAO;
import DAO.EventRequestDAO;
import DAO.SpeakerDAO;
import DAO.CategoryTicketDAO;
import DAO.EventSeatLayoutDAO;

import DTO.Event;
import DTO.EventRequest;
import DTO.Speaker;
import DTO.CategoryTicket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/events/update-details")
public class UpdateEventDetailsController extends HttpServlet {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final EventDAO eventDAO = new EventDAO();
    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();
    private final SpeakerDAO speakerDAO = new SpeakerDAO();
    private final CategoryTicketDAO categoryTicketDAO = new CategoryTicketDAO();
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();

    private static class UpdateEventDetailsRequest {
        Integer eventId;
        SpeakerDTO speaker;
        List<TicketDTO> tickets;
        String bannerUrl;
    }

    private static class SpeakerDTO {
        String fullName;
        String bio;
        String email;
        String phone;
        String avatarUrl;
    }

    private static class TicketDTO {
        String name;
        String description;
        BigDecimal price;
        Integer maxQuantity;
        String status;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handleUpdate(req, resp);
    }

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        try {
            // ======================= (1) AUTH + ROLE =======================
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.write("{\"error\":\"Missing or invalid token\"}");
                return;
            }

            String token = authHeader.substring(7);
            JwtUtils.JwtUser jwtUser = JwtUtils.parseToken(token);
            if (jwtUser == null) {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.write("{\"error\":\"Invalid token\"}");
                return;
            }

            int userId = jwtUser.getUserId();
            String role = jwtUser.getRole();

            if (!"ORGANIZER".equalsIgnoreCase(role)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.write("{\"error\":\"Only Organizer can update event details\"}");
                return;
            }

            // ======================= (2) READ BODY JSON =======================
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            UpdateEventDetailsRequest body = gson.fromJson(sb.toString(), UpdateEventDetailsRequest.class);

            if (body == null || body.eventId == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"Missing eventId\"}");
                return;
            }

            // ======================= (3) GET EVENT =======================
            Event event = eventDAO.getEventById(body.eventId);
            if (event == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":\"Event not found\"}");
                return;
            }

            // ======================= (4) CHECK OWNER =======================
            EventRequest eventRequest = eventRequestDAO.getByCreatedEventId(body.eventId);
            if (eventRequest == null || !eventRequest.getRequesterId().equals(userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.write("{\"error\":\"You are not the owner of this event request\"}");
                return;
            }

            // ======================= (5) CHECK EVENT STATUS =======================
            if ("CANCELLED".equalsIgnoreCase(event.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"Event is not editable in current status\"}");
                return;
            }

            // ======================= (6) VALIDATE INPUT =======================
            // Tickets có thể optional nếu bạn muốn chỉ update speaker/banner.
            // Nhưng theo UI của bạn thì tickets luôn có => mình giữ check này.
            if (body.tickets == null || body.tickets.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"At least one ticket type is required\"}");
                return;
            }

            // Rule: nếu event đang OPEN => không cho đổi quantity
            //Biết đâu là event đầu để mở event đâu là update những lần sau
            boolean lockQuantity = "OPEN".equalsIgnoreCase(event.getStatus());

            // ======================= (7) TRANSACTION =======================
            Connection conn = null;
            try {
                conn = mylib.DBUtils.getConnection();
                conn.setAutoCommit(false);

                // ---------- 7.1 Speaker: update an toàn ----------
                if (body.speaker != null) {
                    // Nếu event đã có speaker_id => update speaker đó
                    // Nếu chưa có => insert mới + gán speaker_id
                    Integer currentSpeakerId = event.getSpeakerId();

                    if (currentSpeakerId != null) {
                        Speaker sp = new Speaker();
                        sp.setSpeakerId(currentSpeakerId);
                        sp.setFullName(body.speaker.fullName);
                        sp.setBio(body.speaker.bio);
                        sp.setEmail(body.speaker.email);
                        sp.setPhone(body.speaker.phone);
                        sp.setAvatarUrl(body.speaker.avatarUrl);

                        // Bạn cần có hàm updateSpeaker(conn, sp)
                        speakerDAO.updateSpeaker(conn, sp);

                    } else {
                        Speaker sp = new Speaker();
                        sp.setFullName(body.speaker.fullName);
                        sp.setBio(body.speaker.bio);
                        sp.setEmail(body.speaker.email);
                        sp.setPhone(body.speaker.phone);
                        sp.setAvatarUrl(body.speaker.avatarUrl);

                        Integer newSpeakerId = speakerDAO.insertSpeaker(conn, sp);
                        if (newSpeakerId != null) {
                            eventDAO.updateSpeakerForEvent(conn, body.eventId, newSpeakerId);
                        }
                    }
                }

                // ---------- 7.2 Banner ----------
                if (body.bannerUrl != null) {
                    eventDAO.updateBannerUrlForEvent(conn, body.eventId, body.bannerUrl);
                }

                // ---------- 7.3 Update ticket info (KHÔNG DELETE) ----------
                for (TicketDTO t : body.tickets) {
                    if (t == null || t.name == null || t.name.trim().isEmpty()) {
                        throw new IllegalArgumentException("Ticket name is required");
                    }
                    if (t.price == null || t.price.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Ticket price must be > 0");
                    }

                    String normalizedName = t.name.trim();
                    String status = (t.status != null && !t.status.trim().isEmpty()) ? t.status.trim() : "ACTIVE";

                    // Tìm ticket type hiện tại theo (eventId, name)
                    CategoryTicket existing = categoryTicketDAO.getByEventIdAndName(conn, body.eventId, normalizedName);

                    if (existing == null) {
                        // Nếu event OPEN mà lại thêm loại vé mới => thường nên chặn.
                        // Nếu bạn muốn chặn cứng thì throw:
                        // throw new RuntimeException("EVENT_OPEN_CANNOT_ADD_NEW_TICKET_TYPE");

                        // Mặc định: vẫn cho insert nếu chưa tồn tại (linh hoạt)
                        CategoryTicket ct = new CategoryTicket();
                        ct.setEventId(body.eventId);
                        ct.setName(normalizedName);
                        ct.setDescription(t.description);
                        ct.setPrice(t.price);
                        ct.setMaxQuantity(t.maxQuantity != null ? t.maxQuantity : 0); // nếu OPEN bạn có thể yêu cầu bắt buộc
                        ct.setStatus(status);

                        categoryTicketDAO.insertCategoryTicket(conn, ct);

                    } else {
                        // ✅ Update mô tả / giá / status
                        categoryTicketDAO.updateTicketInfoExceptQuantity(
                                conn,
                                existing.getCategoryTicketId(),
                                t.description,
                                t.price,
                                status
                        );

                        // ❌ Quantity:
                        // - nếu event chưa OPEN thì cho đổi quantity (tuỳ bạn)
                        // - nếu OPEN thì bỏ qua quantity (lock)
                        if (!lockQuantity) {
                            if (t.maxQuantity != null && t.maxQuantity > 0) {
                                categoryTicketDAO.updateMaxQuantity(
                                        conn,
                                        existing.getCategoryTicketId(),
                                        t.maxQuantity
                                );
                            }
                        }
                    }
                }

                // ---------- 7.4 Layout: nếu OPEN thì không reconfigure ----------
                // Nếu bạn muốn: chỉ reconfigure khi event chưa OPEN
                if (!lockQuantity) {
                    int vipCount = 0;
                    int standardCount = 0;

                    for (TicketDTO t : body.tickets) {
                        int q = (t.maxQuantity != null) ? t.maxQuantity : 0;
                        String typeName = (t.name != null) ? t.name.trim().toUpperCase() : "";
                        if (typeName.contains("VIP")) vipCount += q;
                        else standardCount += q;
                    }

                    int areaId = event.getAreaId();
                    eventSeatLayoutDAO.reconfigureSeatsForEvent(
                            conn,
                            body.eventId,
                            areaId,
                            vipCount,
                            standardCount
                    );
                }

                // ---------- 7.5 Nếu event chưa OPEN, bạn có thể set OPEN ở đây ----------
                // Còn event OPEN rồi thì thôi.
                if (!"OPEN".equalsIgnoreCase(event.getStatus())) {
                    boolean updatedStatus = eventDAO.updateEventStatus(conn, body.eventId, "OPEN");
                    if (!updatedStatus) {
                        throw new RuntimeException("Failed to update event status to OPEN");
                    }
                }

                conn.commit();

                resp.setStatus(HttpServletResponse.SC_OK);
                out.write("{\"message\":\"Event details updated successfully\"}");

            } catch (Exception e) {
                if (conn != null) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                }
                e.printStackTrace();

                String msg = e.getMessage();

                // Nếu bạn bật chặn add ticket type khi OPEN:
                if ("EVENT_OPEN_CANNOT_ADD_NEW_TICKET_TYPE".equals(msg)) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\":\"Event is OPEN. Cannot add new ticket types.\"}");
                    return;
                }

                if (msg != null && msg.startsWith("Not enough physical seats")) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\":\"" + msg + "\"}");
                } else if (msg != null && (msg.contains("Ticket price") || msg.contains("Ticket name"))) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\":\"" + msg + "\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"error\":\"Internal server error\"}");
                }
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ignored) {}
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"error\":\"Unexpected error\"}");
        }
    }

    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || origin.contains("ngrok.app"));

        if (allowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            res.setHeader("Access-Control-Allow-Origin", "null");
        }

        res.setHeader("Vary", "Origin");
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
