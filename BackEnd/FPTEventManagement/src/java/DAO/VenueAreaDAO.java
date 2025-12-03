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

    public List<VenueArea> getAvailableAreasByVenueId(int venueId) {
        List<VenueArea> list = new ArrayList<>();
        String sql = "SELECT area_id, venue_id, area_name, floor, capacity, status FROM Venue_Area WHERE venue_id = ? AND status = 'AVAILABLE' ORDER BY area_name";

        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
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
            System.err.println("[ERROR] getAvailableAreasByVenueId: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public boolean createArea(VenueArea va) {
        String sql = "INSERT INTO Venue_Area(venue_id, area_name, floor, capacity, status) VALUES(?, ?, ?, ?, ?)";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, va.getVenueId());
            ps.setNString(2, va.getAreaName());
            ps.setNString(3, va.getFloor());
            ps.setInt(4, va.getCapacity());
            ps.setNString(5, va.getStatus() == null ? "AVAILABLE" : va.getStatus());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] createArea: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateArea(VenueArea va) {
        String sql = "UPDATE Venue_Area SET area_name = ?, floor = ?, capacity = ? WHERE area_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, va.getAreaName());
            ps.setNString(2, va.getFloor());
            ps.setInt(3, va.getCapacity());
            ps.setInt(4, va.getAreaId());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateArea: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean softDeleteArea(int areaId) {
        String sql = "UPDATE Venue_Area SET status = 'UNAVAILABLE' WHERE area_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] softDeleteArea: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean areaExists(int areaId) {
        String sql = "SELECT 1 FROM Venue_Area WHERE area_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] areaExists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
