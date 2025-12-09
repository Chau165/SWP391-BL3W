package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;

@WebServlet("/api/event-requests/my")
public class GetMyEventRequestsController extends HttpServlet {

    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // ===== 1. Auth + role =====
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Thiếu token\"}");
            return;
        }

        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Token không hợp lệ\"}");
            return;
        }

        String role = JwtUtils.getRoleFromToken(token);
        Integer userId = JwtUtils.getIdFromToken(token);

        if (userId == null || role == null || !"ORGANIZER".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ ORGANIZER mới được xem request của mình\"}");
            return;
        }

        // ===== 2. Lấy list request của organizer =====
        // DAO đã join thêm tên người duyệt (processedByName)
        List<EventRequest> list = eventRequestDAO.getRequestsByStudent(userId);

        // Trả về json list luôn (gồm: requesterName, processedByName, ...)
        String json = gson.toJson(list);
        out.print(json);
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
