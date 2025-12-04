package DAO;

import DTO.Venue;
import DTO.VenueArea;
import mylib.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class VenueDAO {

    // Get all venues. For each venue, fetch its areas using a separate query (legacy JDBC friendly).
    public List<Venue> getAllVenues() {
        List<Venue> list = new ArrayList<>();
        String sql = "SELECT venue_id, venue_name, location, status FROM Venue ORDER BY venue_name";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Venue v = new Venue();
                int venueId = rs.getInt("venue_id");
                v.setVenueId(venueId);
                v.setVenueName(rs.getString("venue_name"));
                v.setAddress(rs.getString("location"));
                v.setStatus(rs.getString("status"));

                // Fetch areas for this venue using a sub-query
                List<VenueArea> areas = getAreasForVenue(conn, venueId);
                v.setAreas(areas);

                list.add(v);
            }
        } catch (Exception e) {
            System.err.println("[ERROR] VenueDAO.getAllVenues: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    // Helper: fetch areas for a given venue using the same connection
    private List<VenueArea> getAreasForVenue(Connection conn, int venueId) {
        List<VenueArea> list = new ArrayList<>();
        String sql = "SELECT area_id, venue_id, area_name, floor, capacity, status FROM Venue_Area WHERE venue_id = ? ORDER BY area_name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VenueArea va = new VenueArea();
                    va.setAreaId(rs.getInt("area_id"));
                    va.setVenueId(rs.getInt("venue_id"));
                    va.setAreaName(rs.getString("area_name"));
                    va.setFloor(rs.getString("floor"));
                    va.setCapacity(rs.getInt("capacity"));
                    va.setStatus(rs.getString("status"));
                    list.add(va);
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] VenueDAO.getAreasForVenue: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    // Check if venue exists
    public boolean existsVenue(int venueId) {
        String sql = "SELECT 1 FROM Venue WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] existsVenue: " + e.getMessage());
            return false;
        }
    }

    // Create new venue
    public boolean createVenue(String venueName, String location) {
        String sql = "INSERT INTO Venue (venue_name, location, status) VALUES (?, ?, 'AVAILABLE')";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, venueName);
            ps.setString(2, location);
            int inserted = ps.executeUpdate();
            return inserted > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] createVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Update venue (includes status). Map 'address' -> DB column 'location'.
    public boolean updateVenue(int venueId, String venueName, String location, String status) {
        String sql = "UPDATE Venue SET venue_name = ?, location = ?, status = ? WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, venueName);
            ps.setString(2, location);
            ps.setString(3, status);
            ps.setInt(4, venueId);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Soft delete venue (set status to UNAVAILABLE)
    public boolean softDeleteVenue(int venueId) {
        String sql = "UPDATE Venue SET status = N'UNAVAILABLE' WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
            int updated = ps.executeUpdate();
            return updated > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] softDeleteVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
