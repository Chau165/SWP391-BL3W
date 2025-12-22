package controller;

import DAO.ReportDAO;
import com.google.gson.Gson;
import utils.JwtUtils;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/api/staff/reports/process")
public class StaffProcessReportController extends HttpServlet {

    private final ReportDAO reportDAO = new ReportDAO();
    private final Gson gson = new Gson();

    private static class Body {
        Integer reportId;
        String action;   // APPROVE / REJECT
        String staffNote;
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        setCorsHeaders(resp, req);
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // 1) Auth
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
        Integer staffId = JwtUtils.getIdFromToken(token);

        boolean allowed = staffId != null && role != null &&
                ("STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));

        if (!allowed) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            out.print("{\"status\":\"fail\",\"message\":\"Chỉ Staff/Admin mới được xử lý report\"}");
            return;
        }

        // 2) Parse body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        Body body = gson.fromJson(sb.toString(), Body.class);

        if (body == null || body.reportId == null || body.reportId <= 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"reportId không hợp lệ\"}");
            return;
        }

        if (body.action == null || body.action.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"action là bắt buộc (APPROVE/REJECT)\"}");
            return;
        }

        String action = body.action.trim().toUpperCase();
        boolean approve;
        if ("APPROVE".equals(action)) approve = true;
        else if ("REJECT".equals(action)) approve = false;
        else {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"action không hợp lệ\"}");
            return;
        }

        String staffNote = body.staffNote != null ? body.staffNote.trim() : null;

        // 3) Process
        ReportDAO.ProcessResult pr = reportDAO.processReport(body.reportId, staffId, approve, staffNote);

        if (!pr.success) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"" + escapeJson(pr.message) + "\"}");
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);

        if (approve) {
            out.print("{\"status\":\"success\",\"message\":\"" + escapeJson(pr.message) +
                    "\",\"refundAmount\":" + (pr.refundAmount == null ? 0 : pr.refundAmount) + "}");
        } else {
            out.print("{\"status\":\"success\",\"message\":\"" + escapeJson(pr.message) + "\"}");
        }
    }

    // tránh lỗi JSON khi message có dấu "
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
        res.setHeader("Access-Control-Max-Age", "86400");
    }
}
