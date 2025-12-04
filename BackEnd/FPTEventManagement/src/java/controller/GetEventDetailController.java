package controller;

import DAO.EventDAO;
import DTO.EventDetailDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;   // ✅ IMPORT

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

@WebServlet("/api/events/detail")
public class GetEventDetailController extends HttpServlet {

    private final EventDAO eventDAO = new EventDAO();
    // ✅ Cho detail cũng serialize null để bannerUrl luôn có trong JSON
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .create();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        setCorsHeaders(response, request);
        response.setContentType("application/json;charset=UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try ( PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Missing event id\"}");
            }
            return;
        }

        try {
            int eventId = Integer.parseInt(idParam);

            EventDetailDto detail = eventDAO.getEventDetail(eventId);
            if (detail == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                try ( PrintWriter out = response.getWriter()) {
                    out.write("{\"message\":\"Event not found or not open\"}");
                }
                return;
            }

            String json = gson.toJson(detail);
            try ( PrintWriter out = response.getWriter()) {
                out.write(json);
            }

        } catch (NumberFormatException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try ( PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Invalid event id\"}");
            }
        } catch (SQLException | ClassNotFoundException ex) {
            ex.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try ( PrintWriter out = response.getWriter()) {
                out.write("{\"message\":\"Error loading event detail\"}");
            }
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
