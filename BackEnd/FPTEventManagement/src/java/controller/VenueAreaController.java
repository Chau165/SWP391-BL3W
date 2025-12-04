package controller;

import DTO.VenueArea;
import com.google.gson.Gson;
import service.VenueAreaService;
import utils.JwtUtils;
import DAO.VenueAreaDAO;

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

@WebServlet("/api/venues/areas")
public class VenueAreaController extends HttpServlet {

    private final Gson gson = new Gson();
    private final VenueAreaService service = new VenueAreaService();
    private final VenueAreaDAO dao = new VenueAreaDAO();

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

        // Auth: Bearer token required
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

        // Check if venueId parameter exists
        String venueIdStr = req.getParameter("venueId");
        List<VenueArea> areas;

        if (venueIdStr != null && !venueIdStr.trim().isEmpty()) {
            // Get areas by venue ID
            try {
                int venueId = Integer.parseInt(venueIdStr);
                areas = dao.getAreasByVenueId(venueId);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> m = new HashMap<>();
                m.put("status", "fail");
                m.put("message", "Invalid venueId format");
                out.print(gson.toJson(m));
                return;
            }
        } else {
            // Get ALL areas
            areas = dao.getAllAreas();
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        out.print(gson.toJson(areas));
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

        VenueArea area;
        try {
            area = gson.fromJson(sb.toString(), VenueArea.class);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m = new HashMap<>();
            m.put("status", "fail");
            m.put("message", "Malformed JSON");
            out.print(gson.toJson(m));
            return;
        }

        Map<String, Object> result = service.createArea(area);
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
            Map<String, Object> m1 = new HashMap<>();
            m1.put("status", "fail");
            m1.put("message", "Missing token");
            out.print(gson.toJson(m1));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m2 = new HashMap<>();
            m2.put("status", "fail");
            m2.put("message", "Invalid token");
            out.print(gson.toJson(m2));
            return;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m3 = new HashMap<>();
            m3.put("status", "fail");
            m3.put("message", "ADMIN role required");
            out.print(gson.toJson(m3));
            return;
        }

        // Read request body as JSON
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m4 = new HashMap<>();
            m4.put("status", "fail");
            m4.put("message", "Invalid request body");
            out.print(gson.toJson(m4));
            return;
        }

        VenueArea area;
        try {
            area = gson.fromJson(sb.toString(), VenueArea.class);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m5 = new HashMap<>();
            m5.put("status", "fail");
            m5.put("message", "Malformed JSON");
            out.print(gson.toJson(m5));
            return;
        }

        Map<String, Object> result = service.updateArea(area);
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
            Map<String, Object> m6 = new HashMap<>();
            m6.put("status", "fail");
            m6.put("message", "Missing token");
            out.print(gson.toJson(m6));
            return;
        }
        String token = auth.substring(7);
        if (!JwtUtils.validateToken(token)) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> m7 = new HashMap<>();
            m7.put("status", "fail");
            m7.put("message", "Invalid token");
            out.print(gson.toJson(m7));
            return;
        }
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> m8 = new HashMap<>();
            m8.put("status", "fail");
            m8.put("message", "ADMIN role required");
            out.print(gson.toJson(m8));
            return;
        }

        // Accept areaId as query param or in JSON body {"areaId": 1}
        String areaIdStr = req.getParameter("areaId");
        Integer areaId = null;
        if (areaIdStr != null && !areaIdStr.trim().isEmpty()) {
            try {
                areaId = Integer.parseInt(areaIdStr);
            } catch (NumberFormatException ignored) {
            }
        }

        if (areaId == null) {
            // try body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            } catch (Exception ignored) {
            }
            if (sb.length() > 0) {
                try {
                    Map m = gson.fromJson(sb.toString(), Map.class);
                    if (m != null && m.get("areaId") != null) {
                        Double d = (Double) m.get("areaId");
                        areaId = d.intValue();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (areaId == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            Map<String, Object> m9 = new HashMap<>();
            m9.put("status", "fail");
            m9.put("message", "areaId is required");
            out.print(gson.toJson(m9));
            return;
        }

        Map<String, Object> result = service.softDeleteArea(areaId);
        boolean ok2 = Boolean.TRUE.equals(result.get("success"));
        Map<String, Object> respMap2 = new HashMap<>();
        respMap2.put("message", result.get("message"));
        if (ok2) {
            resp.setStatus(HttpServletResponse.SC_OK);
            respMap2.put("status", "success");
            out.print(gson.toJson(respMap2));
        } else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            respMap2.put("status", "fail");
            out.print(gson.toJson(respMap2));
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
