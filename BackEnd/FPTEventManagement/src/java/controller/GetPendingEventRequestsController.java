package controller;

import DAO.EventRequestDAO;
import DTO.EventRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;  // ✅ dùng GsonBuilder

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import utils.JwtUtils;

@WebServlet("/api/staff/event-requests")
public class GetPendingEventRequestsController extends HttpServlet {

    private final EventRequestDAO eventRequestDAO = new EventRequestDAO();
    private final Gson gson = new GsonBuilder()
            .serializeNulls()   // ✅ giữ cả field null
            .create();

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
        if (role == null || (!"STAFF".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role))) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ STAFF/ADMIN mới xem được danh sách request\"}");
            return;
        }

        // ===== 2. Lấy list tất cả request (PENDING / APPROVED / REJECTED) =====
        List<EventRequest> all = eventRequestDAO.getPendingRequests();

        List<EventRequest> pending = new ArrayList<>();
        List<EventRequest> approved = new ArrayList<>();
        List<EventRequest> rejected = new ArrayList<>();

        for (EventRequest r : all) {
            if ("PENDING".equalsIgnoreCase(r.getStatus())) {
                pending.add(r);
            } else if ("APPROVED".equalsIgnoreCase(r.getStatus())) {
                approved.add(r);
            } else if ("REJECTED".equalsIgnoreCase(r.getStatus())) {
                rejected.add(r);
            }
        }

        // ===== 3. Gói lại thành object có 3 field =====
        Map<String, Object> result = new HashMap<>();
        result.put("pending", pending);
        result.put("approved", approved);
        result.put("rejected", rejected);

        String json = gson.toJson(result);
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
