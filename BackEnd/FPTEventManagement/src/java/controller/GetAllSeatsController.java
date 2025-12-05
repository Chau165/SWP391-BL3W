package controller;

import DAO.SeatDAO;
import DTO.Seat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@WebServlet("/api/seats")
public class GetAllSeatsController extends HttpServlet {

    private final SeatDAO seatDAO = new SeatDAO();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        try {
            String areaIdStr = req.getParameter("areaId");
            String seatType = req.getParameter("seatType"); // optional
            String eventIdStr = req.getParameter("eventId");  // dùng để đánh dấu ghế đã được đặt

            if (areaIdStr == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing areaId\"}");
                return;
            }

            int areaId;
            try {
                areaId = Integer.parseInt(areaIdStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid areaId\"}");
                return;
            }

            List<Seat> seats;

            // ✅ Luôn lấy FULL danh sách ghế trong area (có thể filter theo seatType)
            if (seatType != null && !seatType.trim().isEmpty()) {
                seats = seatDAO.getSeatsByVenueAndType(areaId, seatType.trim());
            } else {
                seats = seatDAO.getSeatsByVenue(areaId);
            }

            // Nếu có eventId → đánh dấu ghế nào đã được đặt trong event đó
            if (eventIdStr != null && !eventIdStr.trim().isEmpty()) {
                int eventId;
                try {
                    eventId = Integer.parseInt(eventIdStr);
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid eventId\"}");
                    return;
                }

                if (seats != null) {
                    for (Seat s : seats) {
                        boolean booked = seatDAO.isSeatAlreadyBookedForEvent(eventId, s.getSeatId());

                        // 🚩 Ghi đè status để FE dễ hiểu:
                        // - "BOOKED": ghế đã có ticket trong event này
                        // - "AVAILABLE": ghế chưa được đặt trong event này
                        s.setStatus(booked ? "BOOKED" : "AVAILABLE");
                    }
                }
            }
            // Nếu KHÔNG có eventId → giữ nguyên status như trong DB (ACTIVE/INACTIVE)

            // 🔥 SẮP XẾP LẠI DANH SÁCH GHẾ THEO THỨ TỰ RÕ RÀNG
            if (seats != null) {
                seats.sort(new Comparator<Seat>() {
                    @Override
                    public int compare(Seat s1, Seat s2) {
                        // So sánh theo row_no (A, B, C,...)
                        String r1 = s1.getRowNo() != null ? s1.getRowNo() : "";
                        String r2 = s2.getRowNo() != null ? s2.getRowNo() : "";
                        int cmpRow = r1.compareToIgnoreCase(r2);
                        if (cmpRow != 0) return cmpRow;

                        // Nếu cùng row → so sánh theo col_no (chuyển sang số để tránh A1, A10, A2)
                        int c1 = parseColNumber(s1.getColNo());
                        int c2 = parseColNumber(s2.getColNo());
                        return Integer.compare(c1, c2);
                    }
                });
            }

            SeatResponse seatResponse = new SeatResponse();
            seatResponse.setAreaId(areaId);
            seatResponse.setSeatType(seatType);
            seatResponse.setTotal(seats != null ? seats.size() : 0);
            seatResponse.setSeats(seats);

            String json = gson.toJson(seatResponse);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write(json);

        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Server error while loading seats\"}");
        }
    }

    // Helper: parse col_no (NVARCHAR) về số, nếu lỗi thì cho = 0
    private static int parseColNumber(String colNo) {
        if (colNo == null) return 0;
        try {
            return Integer.parseInt(colNo.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Class nhỏ để wrap response
    private static class SeatResponse {

        private int areaId;       // 🔁 thay venueId -> areaId
        private String seatType;
        private int total;
        private List<Seat> seats;

        public int getAreaId() {
            return areaId;
        }

        public void setAreaId(int areaId) {
            this.areaId = areaId;
        }

        public String getSeatType() {
            return seatType;
        }

        public void setSeatType(String seatType) {
            this.seatType = seatType;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public List<Seat> getSeats() {
            return seats;
        }

        public void setSeats(List<Seat> seats) {
            this.seats = seats;
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
