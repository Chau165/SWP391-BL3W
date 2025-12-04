package DAO;

import DTO.VenueArea;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VenueAreaDAO {

    /**
     * Lấy danh sách Area KHÔNG có Event nào trùng khoảng [start, end]
     * với buffer 1 giờ trước & sau.Rule overlap với buffer 1h:
   existing.start_time < (end + 1h)
   AND existing.end_time > (start - 1h)
     *
     * @param start
     * @param end
     * @return 
     */
    public List<VenueArea> getFreeAreasWith1hBuffer(Timestamp start, Timestamp end) {
        List<VenueArea> list = new ArrayList<>();

        // ===== Tính buffer 1h =====
        long ONE_HOUR_MS = 60L * 60L * 1000L;
        Timestamp startBuffer = new Timestamp(start.getTime() - ONE_HOUR_MS); // start - 1h
        Timestamp endBuffer   = new Timestamp(end.getTime() + ONE_HOUR_MS);   // end + 1h

        String sql =
                "SELECT va.area_id, va.venue_id, va.area_name, va.floor, va.capacity, va.status " +
                "FROM Venue_Area va " +
                "WHERE va.status = 'AVAILABLE' " +
                "  AND NOT EXISTS ( " +
                "      SELECT 1 FROM Event e " +
                "      WHERE e.area_id = va.area_id " +
                "        AND e.status IN ('OPEN','CLOSED','DRAFT') " +
                "        AND e.start_time < ? " +   // endBuffer
                "        AND e.end_time   > ? " +   // startBuffer
                "  ) " +
                "ORDER BY va.venue_id, va.area_name";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, endBuffer);
            ps.setTimestamp(2, startBuffer);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VenueArea va = new VenueArea();
                    va.setAreaId(rs.getInt("area_id"));
                    va.setVenueId(rs.getInt("venue_id"));
                    va.setAreaName(rs.getNString("area_name"));
                    va.setFloor(rs.getNString("floor"));
                    va.setCapacity(rs.getInt("capacity"));
                    va.setStatus(rs.getNString("status"));
                    list.add(va);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getFreeAreasWith1hBuffer: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}
