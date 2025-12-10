package DAO;

import DTO.VenueArea;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VenueAreaDAO {

    /**
     * Lấy danh sách Area KHÔNG có Event nào trùng khoảng [start, end] với
     * buffer 1 giờ trước & sau.Rule overlap với buffer 1h: existing.start_time < (end + 1h)
     * AND existing.end_time > (start - 1h)
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
        Timestamp endBuffer = new Timestamp(end.getTime() + ONE_HOUR_MS);   // end + 1h

        String sql
                = "SELECT va.area_id, va.venue_id, va.area_name, va.floor, va.capacity, va.status "
                + "FROM Venue_Area va "
                + "WHERE va.status = 'AVAILABLE' "
                + "  AND NOT EXISTS ( "
                + "      SELECT 1 FROM Event e "
                + "      WHERE e.area_id = va.area_id "
                + "        AND e.status IN ('OPEN','CLOSED','DRAFT') "
                + "        AND e.start_time < ? "
                + // endBuffer
                "        AND e.end_time   > ? "
                + // startBuffer
                "  ) "
                + "ORDER BY va.venue_id, va.area_name";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, endBuffer);
            ps.setTimestamp(2, startBuffer);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VenueArea va  = new VenueArea();
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

    // Get all venue areas (return ALL statuses for admin view)
    public List<VenueArea> getAllAreas() {
        List<VenueArea> list = new ArrayList<>();
        String sql = "SELECT area_id, venue_id, area_name, floor, capacity, status FROM Venue_Area ORDER BY venue_id, area_name";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql);  ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                VenueArea va  = new VenueArea();
                va.setAreaId(rs.getInt("area_id"));
                va.setVenueId(rs.getInt("venue_id"));
                va.setAreaName(rs.getNString("area_name"));
                va.setFloor(rs.getNString("floor"));
                va.setCapacity(rs.getInt("capacity"));
                va.setStatus(rs.getNString("status"));
                list.add(va);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getAllAreas: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // Get areas by venue_id (return ALL statuses)
    public List<VenueArea> getAreasByVenueId(int venueId) {
        List<VenueArea> list = new ArrayList<>();
        String sql = "SELECT area_id, venue_id, area_name, floor, capacity, status FROM Venue_Area WHERE venue_id = ? ORDER BY area_name";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);

            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VenueArea va  = new VenueArea();
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
            System.err.println("[ERROR] getAreasByVenueId: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    // Create new venue area
    public int createArea(int venueId, String areaName, String floor, int capacity)
            throws SQLException, ClassNotFoundException {

        String sql = "INSERT INTO Venue_Area (venue_id, area_name, floor, capacity, status) "
                + "VALUES (?, ?, ?, ?, 'AVAILABLE')";

        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, venueId);
            ps.setString(2, areaName);
            ps.setString(3, floor);
            ps.setInt(4, capacity);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                return -1; // insert fail
            }

            try ( ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // 👈 area_id vừa tạo
                }
            }
        }

        return -1;
    }

    // Check if an area exists by area_id
    public boolean existsArea(int areaId) {
        String sql = "SELECT 1 FROM Venue_Area WHERE area_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            try ( ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] existsArea: " + e.getMessage());
            return false;
        }
    }

    // Update area - INCLUDES STATUS COLUMN (CRITICAL FIX)
    public boolean updateArea(int areaId, String areaName, String floor, int capacity, String status) {
        String sql = "UPDATE Venue_Area SET area_name = ?, floor = ?, capacity = ?, status = ? WHERE area_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, areaName);
            ps.setNString(2, floor);
            ps.setInt(3, capacity);
            ps.setNString(4, status);
            ps.setInt(5, areaId);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateArea: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Soft delete: set status = 'UNAVAILABLE'
    public boolean softDeleteArea(int areaId) {
        String sql = "UPDATE Venue_Area SET status = N'UNAVAILABLE' WHERE area_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] softDeleteArea: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
        // Get single VenueArea by area_id
    public VenueArea getVenueAreaById(int areaId) {
        String sql = "SELECT area_id, venue_id, area_name, floor, capacity, status FROM Venue_Area WHERE area_id = ?";
        try ( Connection conn = DBUtils.getConnection();  PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, areaId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    VenueArea va  = new VenueArea();
                    va.setAreaId(rs.getInt("area_id"));
                    va.setVenueId(rs.getInt("venue_id"));
                    va.setAreaName(rs.getNString("area_name"));
                    va.setFloor(rs.getNString("floor"));
                    va.setCapacity(rs.getInt("capacity"));
                    va.setStatus(rs.getNString("status"));
                    return va;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] getVenueAreaById: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
