package controller;

import DAO.ReportDAO;
import DTO.Report;

import com.google.gson.Gson;

import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.sql.Timestamp;

@WebServlet("/api/student/reports")
public class StudentCreateReportController extends HttpServlet {

    private final ReportDAO reportDAO = new ReportDAO();
    private final Gson gson = new Gson();

    // Body FE gửi lên
    private static class CreateReportBody {
        Integer ticketId;
        String title;
        String description;
        String imageUrl;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        // (0) CORS + encoding
        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        PrintWriter out = resp.getWriter();

        // =========================================================
        // (1) AUTH: Bearer token + validate role STUDENT
        // =========================================================
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
        Integer studentId = JwtUtils.getIdFromToken(token);

        if (studentId == null || role == null || !"STUDENT".equalsIgnoreCase(role)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Student mới được gửi report\"}");
            return;
        }

        // =========================================================
        // (2) Đọc JSON body
        // =========================================================
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        CreateReportBody body = gson.fromJson(sb.toString(), CreateReportBody.class);

        // =========================================================
        // (3) Validate input
        // =========================================================
        if (body == null || body.ticketId == null || body.ticketId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"ticketId không hợp lệ\"}");
            return;
        }

        if (isBlank(body.description)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"description không được để trống\"}");
            return;
        }

        // Ticket phải thuộc student
        if (!reportDAO.isTicketOwnedByUser(body.ticketId, studentId)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Ticket không thuộc về bạn\"}");
            return;
        }

        // Tránh trùng report pending (optional)
        if (reportDAO.hasPendingReportForTicket(body.ticketId)) {
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            out.print("{\"status\":\"fail\",\"message\":\"Ticket này đã có report đang chờ duyệt\"}");
            return;
        }

        // =========================================================
        // (4) Map DTO + insert DB
        // =========================================================
        Report r = new Report();
        r.setUserId(studentId);
        r.setTicketId(body.ticketId);
        r.setTitle(body.title != null ? body.title.trim() : null);
        r.setDescription(body.description.trim());
        r.setImageUrl(body.imageUrl != null ? body.imageUrl.trim() : null);

        int newId = reportDAO.insertReport(r);

        if (newId <= 0) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"error\",\"message\":\"Không tạo được report\"}");
            return;
        }

        // =========================================================
        // (5) Response
        // =========================================================
        resp.setStatus(HttpServletResponse.SC_CREATED);
        out.print("{\"status\":\"success\",\"message\":\"Gửi report thành công\",\"reportId\":" + newId + "}");
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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
