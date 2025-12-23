package controller;

/**
 * ========================================================================================================
 * CONTROLLER: VenueController - CRUD ĐỊA ĐIỂM TỔ CHỨC (VENUE)
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - CRUD operations cho Venue (địa điểm tổ chức sự kiện)
 * - GET: Public (không cần JWT) - Lấy danh sách với nested areas
 * - POST/PUT/DELETE: Bắt buộc JWT + STAFF role
 * - Hiển thị venue và các khu vực (areas) cho ORGANIZER tạo event
 * 
 * ENDPOINT: /api/venues
 * - GET: Lấy tất cả venues với nested areas (Public)
 * - POST: Tạo venue mới (STAFF only)
 * - PUT: Cập nhật venue (STAFF only)
 * - DELETE: Soft delete venue (STAFF only)
 * 
 * DATABASE:
 * - Venue: Địa điểm tổ chức (ví dụ: FPT University, Conference Center...)
 * - Venue_Area: Khu vực trong venue (Hall A, Room 101...)
 * - Quan hệ: Venue 1:N Venue_Area
 * 
 * GET /api/venues - LẤY DANH SÁCH VENUES:
 * - Public endpoint (không cần JWT)
 * - Trả về List<Venue> với nested areas
 * - VenueDAO.getAllVenues() query LEFT JOIN Venue_Area
 * - Response: [{ venueId, venueName, address, status, areas: [...] }]
 * 
 * POST /api/venues - TẠO VENUE MỚI:
 * - STAFF role required
 * - Request body: { venueName, address }
 * - VenueService.createVenue() validate và insert DB
 * - Default status = "AVAILABLE"
 * - Return: { status: "success", message: "Venue created" }
 * 
 * PUT /api/venues - CẬP NHẬT VENUE:
 * - STAFF role required
 * - Request body: { venueId, venueName, address, status }
 * - VenueService.updateVenue() validate và update DB
 * - Kiểm tra venueId tồn tại trước khi update
 * 
 * DELETE /api/venues?id=X - XÓA VENUE:
 * - STAFF role required
 * - Soft delete: Set status = "UNAVAILABLE"
 * - VenueService.softDeleteVenue(venueId)
 * - Không xóa vật lý khỏi DB (giữ dữ liệu lịch sử)
 * 
 * AUTHORIZATION:
 * - JWT token từ header "Authorization: Bearer <token>"
 * - JwtUtils.validateToken() kiểm tra token hợp lệ
 * - JwtUtils.getRoleFromToken() lấy role
 * - Chỉ STAFF mới được CUD operations
 * 
 * USE CASES:
 * - FE: Hiển thị dropdown chọn venue khi ORGANIZER tạo event
 * - Admin: Quản lý danh sách venues và areas
 * - ORGANIZER: Xem venue trống để đặt lịch event
 * 
 * KẾT NỐI FILE:
 * - Service: service/VenueService.java (business logic)
 * - DAO: DAO/VenueDAO.java (database operations)
 * - DTO: DTO/Venue.java, DTO/VenueArea.java
 * - Utils: utils/JwtUtils.java (authentication)
 * - Related: controller/GetFreeAreasController.java (kiểm tra area trống)
 */

import DTO.Venue;
import com.google.gson.Gson;
import service.VenueService;
import utils.JwtUtils;
import DAO.VenueDAO;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@WebServlet("/api/venues")
public class VenueController extends HttpServlet {

    private final Gson gson = new Gson();
    private final VenueService service = new VenueService();
    private final VenueDAO dao = new VenueDAO();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();
        // Public: Get all venues WITH nested areas (one query with LEFT JOIN)
        List<Venue> venues = dao.getAllVenues();
        resp.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(venues));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // Auth: Bearer token required and role = ADMIN
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Missing token");
            out.print(gson.toJson(m));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Invalid token");
            out.print(gson.toJson(m));
            return;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "ADMIN role required");
            out.print(gson.toJson(m));
            return;
        }

        // Read request body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Invalid request body");
            out.print(gson.toJson(m));
            return;
        }

        Venue venue;
        try {
            venue = gson.fromJson(sb.toString(), Venue.class);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Malformed JSON");
            out.print(gson.toJson(m));
            return;
        }

        Map<String, Object> result = service.createVenue(venue);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("message", result.get("message"));
        if (ok) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
            respMap.put("status", "success");
            out.print(gson.toJson(respMap));
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            respMap.put("status", "fail");
            out.print(gson.toJson(respMap));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // Auth: Bearer token required and role = ADMIN
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Missing token");
            out.print(gson.toJson(m));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Invalid token");
            out.print(gson.toJson(m));
            return;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "ADMIN role required");
            out.print(gson.toJson(m));
            return;
        }

        // Read request body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Invalid request body");
            out.print(gson.toJson(m));
            return;
        }
        // Parse JSON body explicitly for the required fields: venueId, venueName,
        // address, status
        Map body;
        try {
            body = gson.fromJson(sb.toString(), Map.class);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Malformed JSON");
            out.print(gson.toJson(m));
            return;
        }

        Integer venueId = null;
        try {
            if (body.get("venueId") != null) {
                Double d = (Double) body.get("venueId");
                venueId = d.intValue();
            }
        } catch (Exception ignored) {
        }

        String venueName = body.get("venueName") == null ? null : body.get("venueName").toString();
        String address = body.get("address") == null ? null : body.get("address").toString();
        String status = body.get("status") == null ? null : body.get("status").toString();

        if (venueId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "venueId is required");
            out.print(gson.toJson(m));
            return;
        }

        if (venueName == null || venueName.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "venueName is required");
            out.print(gson.toJson(m));
            return;
        }

        // Build Venue DTO and call service
        Venue v = new Venue();
        v.setVenueId(venueId);
        v.setVenueName(venueName);
        v.setAddress(address == null ? "" : address);
        v.setStatus(status == null ? "AVAILABLE" : status);

        Map<String, Object> result = service.updateVenue(v);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("message", result.get("message"));
        if (ok) {
            resp.setStatus(HttpServletResponse.SC_OK);
            respMap.put("status", "success");
            out.print(gson.toJson(respMap));
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            respMap.put("status", "fail");
            out.print(gson.toJson(respMap));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // Auth: Bearer token required and role = ADMIN
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Missing token");
            out.print(gson.toJson(m));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Invalid token");
            out.print(gson.toJson(m));
            return;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "ADMIN role required");
            out.print(gson.toJson(m));
            return;
        }

        // Get venueId from query param or body
        String venueIdStr = req.getParameter("venueId");
        Integer venueId = null;
        if (venueIdStr != null && !venueIdStr.trim().isEmpty()) {
            try {
                venueId = Integer.parseInt(venueIdStr);
            } catch (NumberFormatException ignored) {
            }
        }

        if (venueId == null) {
            // Try body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null)
                    sb.append(line);
            } catch (Exception ignored) {
            }
            if (sb.length() > 0) {
                try {
                    Map m = gson.fromJson(sb.toString(), Map.class);
                    if (m != null && m.get("venueId") != null) {
                        Double d = (Double) m.get("venueId");
                        venueId = d.intValue();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (venueId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "venueId is required");
            out.print(gson.toJson(m));
            return;
        }

        Map<String, Object> result = service.softDeleteVenue(venueId);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> respMap = new HashMap<>();
        respMap.put("message", result.get("message"));
        if (ok) {
            resp.setStatus(HttpServletResponse.SC_OK);
            respMap.put("status", "success");
            out.print(gson.toJson(respMap));
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            respMap.put("status", "fail");
            out.print(gson.toJson(respMap));
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
        res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers",
                "Content-Type, Authorization, ngrok-skip-browser-warning");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}