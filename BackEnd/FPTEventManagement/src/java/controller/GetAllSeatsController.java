package controller;

import DAO.SeatDAO;
import DAO.EventSeatLayoutDAO;
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

    private final SeatDAO seatDAO = new SeatDAO();                     // ghế vật lý
    private final EventSeatLayoutDAO eventSeatLayoutDAO = new EventSeatLayoutDAO();  // layout theo event
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setCorsHeaders(resp, req);
        resp.setContentType("application/json;charset=UTF-8");

        try {
            String areaIdStr  = req.getParameter("areaId");
            String seatType   = req.getParameter("seatType"); // optional, dùng cho layout event
            String eventIdStr = req.getParameter("eventId");  // nếu có => ưu tiên lấy layout theo event

            List<Seat> seats;

            Integer eventId = null;
            Integer areaId  = null;

            // ===== CASE 1: CÓ eventId → lấy layout ghế THEO EVENT =====
            if (eventIdStr != null && !eventIdStr.trim().isEmpty()) {
                try {
                    eventId = Integer.parseInt(eventIdStr.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid eventId\"}");
                    return;
                }

                // Lấy toàn bộ ghế cấu hình cho event (JOIN Event_Seat_Layout + Seat)
                seats = eventSeatLayoutDAO.getSeatsForEvent(eventId, seatType);

                // Lấy areaId từ ghế đầu tiên (nếu có)
                if (seats != null && !seats.isEmpty()) {
                    areaId = seats.get(0).getAreaId();
                } else {
                    // Không có layout ghế cho event này → trả empty list
                    areaId = (areaIdStr != null && !areaIdStr.isEmpty())
                            ? Integer.parseInt(areaIdStr)
                            : null;
                }

            // ===== CASE 2: KHÔNG có eventId → chỉ trả ghế VẬT LÝ của area =====
            } else {
                if (areaIdStr == null || areaIdStr.trim().isEmpty()) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Missing areaId or eventId\"}");
                    return;
                }

                try {
                    areaId = Integer.parseInt(areaIdStr.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid areaId\"}");
                    return;
                }

                // Ở mode ghế vật lý, không có seat_type, nên bỏ qua param seatType
                seats = seatDAO.getSeatsByVenue(areaId);
            }

            // 🔥 SẮP XẾP LẠI DANH SÁCH GHẾ THEO THỨ TỰ ROW/COL
            if (seats != null) {
                seats.sort(new Comparator<Seat>() {
                    @Override
                    public int compare(Seat s1, Seat s2) {
                        String r1 = s1.getRowNo() != null ? s1.getRowNo() : "";
                        String r2 = s2.getRowNo() != null ? s2.getRowNo() : "";
                        int cmpRow = r1.compareToIgnoreCase(r2);
                        if (cmpRow != 0) return cmpRow;

                        int c1 = parseColNumber(s1.getColNo());
                        int c2 = parseColNumber(s2.getColNo());
                        return Integer.compare(c1, c2);
                    }
                });
            }

            // Build response
            SeatResponse seatResponse = new SeatResponse();
            seatResponse.setEventId(eventId);
            seatResponse.setAreaId(areaId != null ? areaId : 0);
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

        private Integer eventId;
        private int areaId;
        private String seatType;
        private int total;
        private List<Seat> seats;

        public Integer getEventId() {
            return eventId;
        }

        public void setEventId(Integer eventId) {
            this.eventId = eventId;
        }

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
