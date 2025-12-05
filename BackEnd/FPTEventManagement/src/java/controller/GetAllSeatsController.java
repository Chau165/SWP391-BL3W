package controller;

import DAO.SeatDAO;
import DTO.Seat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
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
            String eventIdStr = req.getParameter("eventId");  // ⭐ THÊM

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

            if (eventIdStr != null && !eventIdStr.trim().isEmpty()) {
                // 🎯 TRƯỜNG HỢP MUỐN LẤY GHẾ CÒN TRỐNG CHO 1 EVENT
                int eventId;
                try {
                    eventId = Integer.parseInt(eventIdStr);
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid eventId\"}");
                    return;
                }

                seats = seatDAO.getAvailableSeatsForEvent(eventId, areaId,
                        (seatType != null && !seatType.trim().isEmpty()) ? seatType.trim() : null);

            } else {
                // 🧱 TRƯỚC GIỜ: LẤY MỌI GHẾ TRONG AREA (KO QUAN TÂM EVENT)
                if (seatType != null && !seatType.trim().isEmpty()) {
                    seats = seatDAO.getSeatsByVenueAndType(areaId, seatType.trim());
                } else {
                    seats = seatDAO.getSeatsByVenue(areaId);
                }
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
