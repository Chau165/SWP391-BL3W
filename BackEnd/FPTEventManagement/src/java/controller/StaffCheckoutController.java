package controller;

import DAO.TicketDAO;
import DAO.EventDAO;
import DTO.Ticket;
import DTO.Event;
import utils.JwtUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import service.SystemConfigService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/staff/checkout")
public class StaffCheckoutController extends HttpServlet {

    private final TicketDAO ticketDAO = new TicketDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");

        String authHeader = req.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"status\":\"FAIL\",\"message\":\"Missing Authorization\"}");
            return;
        }

        String token = authHeader.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"status\":\"FAIL\",\"message\":\"Invalid or expired token\"}");
            return;
        }

        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !(role.equalsIgnoreCase("STAFF") || role.equalsIgnoreCase("ADMIN"))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().write("{\"status\":\"FAIL\",\"message\":\"Permission denied. STAFF or ADMIN only.\"}");
            return;
        }

        String ticketIdStr = req.getParameter("ticketId");
        if (ticketIdStr == null || ticketIdStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"status\":\"FAIL\",\"message\":\"Missing ticketId parameter\"}");
            return;
        }

        int ticketId;
        try {
            ticketId = Integer.parseInt(ticketIdStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"status\":\"FAIL\",\"message\":\"Invalid ticketId\"}");
            return;
        }

        boolean ok = ticketDAO.checkoutTicket(ticketId);
        JsonObject out = new JsonObject();
        if (ok) {
            out.addProperty("status", "SUCCESS");
            out.addProperty("message", "Check-out thành công");
            out.addProperty("ticketId", ticketId);
            resp.setStatus(HttpServletResponse.SC_OK);
        } else {
            out.addProperty("status", "FAIL");
            out.addProperty("message", "Check-out thất bại");
            out.addProperty("ticketId", ticketId);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        resp.getWriter().write(gson.toJson(out));
    }
}
