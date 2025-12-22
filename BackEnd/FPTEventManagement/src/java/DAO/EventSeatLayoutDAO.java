package DAO;

import DTO.Seat;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventSeatLayoutDAO {

    // ============= MAP ROW TỪ JOIN Event_Seat_Layout + Seat + Ticket ============
    private Seat mapRowToSeatForEvent(ResultSet rs) throws SQLException {
        Seat seat = new Seat();
        seat.setSeatId(rs.getInt("seat_id"));
        seat.setAreaId(rs.getInt("area_id"));
        seat.setSeatCode(rs.getString("seat_code"));
        seat.setRowNo(rs.getString("row_no"));
        seat.setColNo(rs.getString("col_no"));
        // status ở đây là status layout cho event: AVAILABLE / BOOKED
        seat.setStatus(rs.getString("layout_status"));
        // seat_type theo event: VIP / STANDARD
        seat.setSeatType(rs.getString("seat_type"));
        return seat;
    }

    // ============= XOÁ LAYOUT CŨ CỦA EVENT ============
    public void deleteByEventId(Connection conn, int eventId) throws SQLException {
        String sql = "DELETE FROM Event_Seat_Layout WHERE event_id = ?";
        try ( PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, eventId);
            ps.executeUpdate();
        }
    }

    // ============= RECONFIG LAYOUT GHẾ CHO EVENT ============
    public void reconfigureSeatsForEvent(
            Connection conn,
            int eventId,
            int areaId,
            int vipCount,
            int standardCount
    ) throws SQLException {

        int totalNeeded = vipCount + standardCount;

        // 1. Lấy danh sách ghế vật lý trong area (chỉ lấy ghế ACTIVE)
        List<Integer> seatIds = new ArrayList<>();

        String selectSql = "SELECT seat_id "
                + "FROM Seat "
                + "WHERE area_id = ? AND status = 'ACTIVE' "
                + "ORDER BY row_no, col_no, seat_code";

        try ( PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, areaId);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seatIds.add(rs.getInt("seat_id"));
                }
            }
        }

        if (seatIds.size() < totalNeeded) {
            throw new RuntimeException(
                    "Not enough physical seats in area_id=" + areaId
                    + " | active seats in DB=" + seatIds.size()
                    + " < required=" + totalNeeded
            );
        }

        // 2. Xoá layout cũ
        deleteByEventId(conn, eventId);

        // 3. Insert layout mới
        String insertSql = "INSERT INTO Event_Seat_Layout (event_id, seat_id, seat_type, status) "
                + "VALUES (?, ?, ?, ?)";

        try ( PreparedStatement psInsert = conn.prepareStatement(insertSql)) {

            int index = 0;

            for (int i = 0; i < totalNeeded; i++) {
                int seatId = seatIds.get(i);

                String seatType;
                if (index < vipCount) {
                    seatType = "VIP";
                } else {
                    seatType = "STANDARD";
                }

                psInsert.setInt(1, eventId);
                psInsert.setInt(2, seatId);
                psInsert.setString(3, seatType);
                psInsert.setString(4, "AVAILABLE"); // ban đầu tất cả là AVAILABLE
                psInsert.addBatch();

                index++;
            }

            psInsert.executeBatch();
        }
    }

    // ============= LẤY DANH SÁCH GHẾ CỦA EVENT (CHO FE RENDER) ============
    public List<Seat> getSeatsForEvent(int eventId, String seatTypeFilter) {
        List<Seat> list = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "SELECT "
                + "   s.seat_id, "
                + "   s.area_id, "
                + "   s.seat_code, "
                + "   s.row_no, "
                + "   s.col_no, "
                + "   esl.seat_type, "
                + "   CASE "
                + "       WHEN EXISTS ( "
                + "           SELECT 1 FROM Ticket t "
                + "           WHERE t.event_id = esl.event_id "
                + "             AND t.seat_id = esl.seat_id "
                + "           AND t.status IN ('BOOKED','CHECKED_IN','CHECKED_OUT', 'REFUND') "
                + "       ) THEN 'BOOKED' "
                + // ✅ đã mua / checkin
                "       WHEN EXISTS ( "
                + "           SELECT 1 FROM Ticket t "
                + "           WHERE t.event_id = esl.event_id "
                + "             AND t.seat_id = esl.seat_id "
                + "             AND t.status = 'PENDING' "
                + "       ) THEN 'HOLD' "
                + // ✅ đang giữ tạm (thanh toán VNPay)
                "       ELSE 'AVAILABLE' "
                + // ✅ còn trống
                "   END AS layout_status "
                + "FROM Event_Seat_Layout esl "
                + "JOIN Seat s ON esl.seat_id = s.seat_id "
                + "WHERE esl.event_id = ? "
                + "  AND s.status = 'ACTIVE' "
        );

        boolean filterByType = (seatTypeFilter != null && !seatTypeFilter.trim().isEmpty());
        if (filterByType) {
            sql.append(" AND esl.seat_type = ? ");
        }

        sql.append(" ORDER BY s.row_no, s.col_no, s.seat_code ");

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            ps.setInt(1, eventId);
            if (filterByType) {
                ps.setString(2, seatTypeFilter.trim());
            }

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToSeatForEvent(rs));
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] getSeatsForEvent: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    // ============= LẤY 1 GHẾ CỤ THỂ TRONG EVENT (DÙNG CHO PAYMENT) ============
    public Seat getSeatForEvent(int eventId, int seatId) {
        String sql
                = "SELECT "
                + "   s.seat_id, "
                + "   s.area_id, "
                + "   s.seat_code, "
                + "   s.row_no, "
                + "   s.col_no, "
                + "   esl.seat_type, "
                + "   CASE "
                + "       WHEN EXISTS ( "
                + "           SELECT 1 FROM Ticket t "
                + "           WHERE t.event_id = esl.event_id "
                + "             AND t.seat_id = esl.seat_id "
                + "             AND t.status IN ('BOOKED','CHECKED_IN') "
                + "       ) THEN 'BOOKED' "
                + "       ELSE 'AVAILABLE' "
                + "   END AS layout_status "
                + "FROM Event_Seat_Layout esl "
                + "JOIN Seat s ON esl.seat_id = s.seat_id "
                + "WHERE esl.event_id = ? "
                + "  AND esl.seat_id = ? "
                + "  AND s.status = 'ACTIVE'";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, eventId);
            ps.setInt(2, seatId);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToSeatForEvent(rs);
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] getSeatForEvent: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
