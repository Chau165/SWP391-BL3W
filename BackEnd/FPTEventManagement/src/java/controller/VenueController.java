package controller;

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
        if (role == null || !"STAFF".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "STAFF role required");
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
        if (role == null || !"STAFF".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "STAFFSTAFF role required");
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
        // Parse JSON body explicitly for the required fields: venueId, venueName, address, status
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
        if (role == null || !"STAFF".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "STAFFSTAFF role required");
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
                while ((line = reader.readLine()) != null) sb.append(line);
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
