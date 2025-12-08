package controller;

import DAO.EventDAO;
import DAO.EventRequestDAO;
import DAO.SpeakerDAO;
import DAO.CategoryTicketDAO;
import DAO.EventSeatLayoutDAO; // ✅ dùng layout theo event

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
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO(); // ✅

    // ====== DTO cho request body ======
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
            // 1. Auth + role
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

            // 2. Read body
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

            // 3. Lấy event
            Event event = eventDAO.getEventById(body.eventId);
            if (event == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("{\"error\":\"Event not found\"}");
                return;
            }

            // 4. Check owner (student requester)
            EventRequest eventRequest = eventRequestDAO.getByCreatedEventId(body.eventId);
            if (eventRequest == null || !eventRequest.getRequesterId().equals(userId)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.write("{\"error\":\"You are not the owner of this event request\"}");
                return;
            }

            // 5. Không cho update nếu event bị CANCELLED
            if ("CANCELLED".equalsIgnoreCase(event.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"Event is not editable in current status\"}");
                return;
            }

            // 6. Validate tickets + max_seats
            if (body.tickets == null || body.tickets.isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"At least one ticket type is required\"}");
                return;
            }

            int sumQuantity = 0;
            int vipCount = 0;
            int standardCount = 0;

            for (TicketDTO t : body.tickets) {
                if (t.maxQuantity == null || t.maxQuantity <= 0) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.write("{\"error\":\"Ticket maxQuantity must be > 0\"}");
                    return;
                }
                sumQuantity += t.maxQuantity;

                String typeName = (t.name != null) ? t.name.trim().toUpperCase() : "";
                if (typeName.contains("VIP")) {
                    vipCount += t.maxQuantity;
                } else {
                    standardCount += t.maxQuantity;
                }
            }

            if (sumQuantity > event.getMaxSeats()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"error\":\"Total ticket quantity exceeds event max_seats\"}");
                return;
            }

            // 7. Transaction
            Connection conn = null;
            try {
                conn = mylib.DBUtils.getConnection();
                conn.setAutoCommit(false);

                // 7.1 Speaker
                Integer speakerId = null;
                if (body.speaker != null) {
                    Speaker sp = new Speaker();
                    sp.setFullName(body.speaker.fullName);
                    sp.setBio(body.speaker.bio);
                    sp.setEmail(body.speaker.email);
                    sp.setPhone(body.speaker.phone);
                    sp.setAvatarUrl(body.speaker.avatarUrl);

                    speakerId = speakerDAO.insertSpeaker(conn, sp);
                }

                if (speakerId != null) {
                    eventDAO.updateSpeakerForEvent(conn, body.eventId, speakerId);
                }

                // 7.3 banner_url
                if (body.bannerUrl != null) {
                    eventDAO.updateBannerUrlForEvent(conn, body.eventId, body.bannerUrl);
                }

                // 7.4 Xoá ticket cũ
                categoryTicketDAO.deleteByEventId(conn, body.eventId);

                // 7.5 Insert ticket mới
                for (TicketDTO t : body.tickets) {
                    CategoryTicket ct = new CategoryTicket();
                    ct.setEventId(body.eventId);
                    ct.setName(t.name);
                    ct.setDescription(t.description);
                    ct.setPrice(t.price);
                    ct.setMaxQuantity(t.maxQuantity);
                    ct.setStatus(t.status != null ? t.status : "ACTIVE");

                    categoryTicketDAO.insertCategoryTicket(conn, ct);
                }

                // 🔥 7.5b: Re-config layout ghế cho riêng event này
                int areaId = event.getAreaId();

                eventSeatLayoutDAO.reconfigureSeatsForEvent(
                        conn,
                        body.eventId,
                        areaId,
                        vipCount,
                        standardCount
                );

                // 7.6 Cập nhật status event → OPEN
                boolean updatedStatus = eventDAO.updateEventStatus(conn, body.eventId, "OPEN");
                if (!updatedStatus) {
                    throw new RuntimeException("Failed to update event status to OPEN");
                }

                conn.commit();

                resp.setStatus(HttpServletResponse.SC_OK);
                out.write("{\"message\":\"Event details updated successfully\"}");

            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {}
                }
                e.printStackTrace();

                String msg = e.getMessage();
                if (msg != null && msg.startsWith("Not enough physical seats")) {
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
