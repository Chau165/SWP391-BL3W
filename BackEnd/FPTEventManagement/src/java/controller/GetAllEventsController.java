package controller;

import DAO.EventDAO;
import DTO.Event;
import DTO.EventListDto;
import com.google.gson.Gson;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/events")
public class GetAllEventsController extends HttpServlet {

    private final EventDAO eventDAO = new EventDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        // ===== 1. Lấy role từ request (JWT Filter đã set trước đó) =====
        String role = (String) request.getAttribute("role");
        if (!isAllowedRole(role)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            try ( PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Forbidden: your role is not allowed to access this resource\"}");
            }
            return;
        }

        // ===== 2. Gọi DAO lấy danh sách Event =====
        try {
            List<Event> events = eventDAO.getAllEvents();

            // Map sang EventListDto để loại bỏ field nhạy cảm
            List<EventListDto> result = new ArrayList<>();
            for (Event e : events) {
                EventListDto dto = new EventListDto(
                        e.getEventId(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getStartTime(),
                        e.getEndTime(),
                        e.getMaxSeats(),
                        e.getStatus()
                );
                result.add(dto);
            }

            // ===== 3. Trả JSON =====
            String json = gson.toJson(result);
            try ( PrintWriter out = response.getWriter()) {
                out.write(json);
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try ( PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Internal server error when loading events\"}");
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(GetAllEventsController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean isAllowedRole(String role) {
        if (role == null) {
            return false;
        }
        switch (role) {
            case "STUDENT":
            case "ORGANIZER":
            case "STAFF":
            case "ADMIN":
                return true;
            default:
                return false;
        }
    }

    private void setCorsHeaders(HttpServletResponse res, HttpServletRequest req) {
        String origin = req.getHeader("Origin");

        boolean allowed = origin != null && (origin.equals("http://localhost:5173")
                || origin.equals("http://127.0.0.1:5173")
                || origin.equals("http://localhost:3000")
                || origin.equals("http://127.0.0.1:3000")
                || origin.contains("ngrok-free.app")
                || // ⭐ Cho phép ngrok
                origin.contains("ngrok.app") // ⭐ (phòng trường hợp domain mới)
                );

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
