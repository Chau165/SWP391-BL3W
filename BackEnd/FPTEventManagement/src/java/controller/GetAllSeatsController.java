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
        resp.setContentType("application/json;charset=UTF-8");

        try {
            // 🔁 ĐỔI TÊN PARAM: areaId (thay vì venueId)
            String areaIdStr = req.getParameter("areaId");
            String seatType = req.getParameter("seatType"); // có thể null

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
            if (seatType != null && !seatType.trim().isEmpty()) {
                // ⚠️ seatDAO.getSeatsByVenueAndType hiện đang dùng tham số như areaId (đã sửa ở DAO)
                seats = seatDAO.getSeatsByVenueAndType(areaId, seatType.trim());
            } else {
                seats = seatDAO.getSeatsByVenue(areaId);
            }

            // Gói lại một object để FE dễ xử lý
            SeatResponse seatResponse = new SeatResponse();
            seatResponse.setAreaId(areaId);            // 🔁 đổi field
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
}
