package DAO;

import DTO.Seat;
import mylib.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SeatDAO {

    // ====================== MAP RESULTSET → DTO ======================
    private Seat mapRowToSeat(ResultSet rs) throws SQLException {
        Seat seat = new Seat();
        seat.setSeatId(rs.getInt("seat_id"));
        seat.setAreaId(rs.getInt("area_id"));          // ✅ area_id thay vì venue_id
        seat.setSeatCode(rs.getString("seat_code"));
        seat.setRowNo(rs.getString("row_no"));
        seat.setColNo(rs.getString("col_no"));
        seat.setStatus(rs.getString("status"));
        seat.setSeatType(rs.getString("seat_type"));   // VIP / STANDARD / ...
        return seat;
    }

    // ====================== GET BY ID ======================
    public Seat getSeatById(int seatId) {
        String sql = "SELECT seat_id, area_id, seat_code, row_no, col_no, "
                + "       status, seat_type "
                + "FROM   Seat WHERE seat_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, seatId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSeat(rs);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getSeatById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // ====================== CHECK BOOKED FOR EVENT ======================
    /**
     * Kiểm tra ghế đã được đặt cho event này chưa Ticket.status: 'BOOKED',
     * 'CHECKED_IN' được xem là đã chiếm ghế
     */
    public boolean isSeatAlreadyBookedForEvent(int eventId, int seatId) {
        String sql = "SELECT COUNT(*) AS cnt "
                + "FROM Ticket "
                + "WHERE event_id = ? AND seat_id = ? "
                + "  AND status IN ('BOOKED','CHECKED_IN')";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, eventId);
            ps.setInt(2, seatId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    return count > 0;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] isSeatAlreadyBookedForEvent: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    // ====================== LIST BY AREA (tên cũ: venue) ======================
    /**
     * Lấy toàn bộ ghế theo area. Lưu ý: tham số venueId bây giờ thực chất là
     * areaId (do đổi schema).
     */
    public List<Seat> getSeatsByVenue(int venueId) {
        int areaId = venueId; // alias cho dễ chuyển code cũ
        List<Seat> list = new ArrayList<>();

        String sql = "SELECT seat_id, area_id, seat_code, row_no, col_no, status, seat_type "
                + "FROM   Seat "
                + "WHERE  area_id = ? "
                + "ORDER BY row_no, col_no";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, areaId);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSeat(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getSeatsByVenue/Area: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // ====================== LIST BY AREA + TYPE ======================
    /**
     * Lấy ghế theo area + loại ghế (seat_type: VIP / STANDARD / ...) Tham số
     * venueId thực chất là areaId.
     */
    public List<Seat> getSeatsByVenueAndType(int venueId, String seatType) {
        int areaId = venueId; // alias

        List<Seat> list = new ArrayList<>();

        String sql = "SELECT seat_id, area_id, seat_code, row_no, col_no, status, seat_type "
                + "FROM   Seat "
                + "WHERE  area_id = ? AND seat_type = ? "
                + "ORDER BY row_no, col_no";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, areaId);
            ps.setString(2, seatType);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSeat(rs));
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getSeatsByVenueAndType/AreaAndType: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public boolean updateSeatStatus(int seatId, String newStatus) {
        String sql = "UPDATE Seat SET status = ? WHERE seat_id = ?";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus);   // 'INACTIVE'
            ps.setInt(2, seatId);

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateSeatStatus: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
