package DAO;

import DTO.Seat;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SeatDAO {

    // ====================== MAP RESULTSET → DTO (GHẾ VẬT LÝ) ======================
    private Seat mapRowToSeat(ResultSet rs) throws SQLException {
        Seat seat = new Seat();
        seat.setSeatId(rs.getInt("seat_id"));
        seat.setAreaId(rs.getInt("area_id"));
        seat.setSeatCode(rs.getString("seat_code"));
        seat.setRowNo(rs.getString("row_no"));
        seat.setColNo(rs.getString("col_no"));
        // status ở đây là status vật lý: ACTIVE / INACTIVE
        seat.setStatus(rs.getString("status"));
        // ghế vật lý không có seat_type
        seat.setSeatType(null);
        return seat;
    }

    // ====================== GET BY ID (GHẾ VẬT LÝ) ======================
    public Seat getSeatById(int seatId) {
        String sql = "SELECT seat_id, area_id, seat_code, row_no, col_no, status " +
                     "FROM Seat WHERE seat_id = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, seatId);

            try (ResultSet rs = ps.executeQuery()) {
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

    // ====================== CHECK BOOKED FOR EVENT (VẪN DÙNG TICKET) ======================
    public boolean isSeatAlreadyBookedForEvent(int eventId, int seatId) {
        String sql = "SELECT COUNT(*) AS cnt " +
                     "FROM Ticket " +
                     "WHERE event_id = ? AND seat_id = ? " +
                     "  AND status IN ('BOOKED','CHECKED_IN')";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, eventId);
            ps.setInt(2, seatId);

            try (ResultSet rs = ps.executeQuery()) {
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

    // ====================== LIST BY AREA (GHẾ VẬT LÝ) ======================
    public List<Seat> getSeatsByVenue(int venueId) {
        int areaId = venueId; // alias
        List<Seat> list = new ArrayList<>();

        String sql = "SELECT seat_id, area_id, seat_code, row_no, col_no, status " +
                     "FROM Seat " +
                     "WHERE area_id = ? " +
                     "ORDER BY row_no, col_no";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, areaId);

            try (ResultSet rs = ps.executeQuery()) {
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

    // 🚨 HÀM CŨ TỪNG GÂY LỖI: GIỜ SỬA LẠI KHÔNG DÙNG seat_type NỮA
    // Ở mode ghế vật lý thì không có khái niệm VIP/Standard, nên
    // cứ trả list ghế theo area, seatType lúc này bỏ qua.
    public List<Seat> getSeatsByVenueAndType(int venueId, String seatType) {
        // Nếu chỗ nào code cũ gọi hàm này, vẫn tránh crash
        return getSeatsByVenue(venueId);
    }

    // ====================== UPDATE STATUS VẬT LÝ ======================
    public boolean updateSeatStatus(int seatId, String newStatus) {
        String sql = "UPDATE Seat SET status = ? WHERE seat_id = ?";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus);
            ps.setInt(2, seatId);

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateSeatStatus: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ====================== GENERATE GHẾ VẬT LÝ CHO AREA ======================
    public void generateSeatsForArea(int areaId, int capacity) throws SQLException, ClassNotFoundException {
        String sql = "INSERT INTO Seat (seat_code, row_no, col_no, status, area_id) " +
                     "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int seatsPerRow = 10;   // 10 ghế / hàng: A1..A10, B1..B10,...
            char row = 'A';
            int col = 1;

            for (int i = 1; i <= capacity; i++) {
                String seatCode = row + String.valueOf(col);
                String rowNo = String.valueOf(row);

                String status = "ACTIVE";

                ps.setString(1, seatCode);
                ps.setString(2, rowNo);
                ps.setInt(3, col);
                ps.setString(4, status);
                ps.setInt(5, areaId);

                ps.addBatch();

                col++;
                if (col > seatsPerRow) {
                    col = 1;
                    row++;
                }

                if (i % 100 == 0) {
                    ps.executeBatch();
                }
            }

            ps.executeBatch();
        }
    }
}
