package controller;

import DAO.EventDAO;
import DTO.Event;
import DTO.EventListDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/events")
public class GetAllEventsController extends HttpServlet {

    private final EventDAO eventDAO = new EventDAO();
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        // ❌ BỎ HOÀN TOÀN CHECK JWT / ROLE
        // ==> Guest không cần đăng nhập vẫn xem được list event

        try {
            // 1) Lấy danh sách Event (OPEN + CLOSED) từ DAO
            List<Event> events = eventDAO.getAllEvents();

            List<EventListDto> openEvents = new ArrayList<>();
            List<EventListDto> closedEvents = new ArrayList<>();

            for (Event e : events) {
                EventListDto dto = new EventListDto(
                        e.getEventId(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getStartTime(),
                        e.getEndTime(),
                        e.getMaxSeats(),
                        e.getStatus(),
                        e.getBannerUrl()
                );

                // Map thêm khu vực
                dto.setAreaId(e.getAreaId());
                dto.setAreaName(e.getAreaName());
                dto.setFloor(e.getFloor());

                // Map thêm địa điểm
                dto.setVenueName(e.getVenueName());
                dto.setVenueLocation(e.getVenueLocation());

                if ("OPEN".equalsIgnoreCase(e.getStatus())) {
                    openEvents.add(dto);
                } else if ("CLOSED".equalsIgnoreCase(e.getStatus())) {
                    closedEvents.add(dto);
                }
            }

            // 2) Đóng gói JSON: { openEvents: [...], closedEvents: [...] }
            Map<String, Object> result = new HashMap<>();
            result.put("openEvents", openEvents);
            result.put("closedEvents", closedEvents);

            String json = gson.toJson(result);
            try (PrintWriter out = response.getWriter()) {
                out.write(json);
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Internal server error when loading events\"}");
            }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(GetAllEventsController.class.getName()).log(Level.SEVERE, null, ex);
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
