package DAO;

import DTO.VenueDTO;
import mylib.DBUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VenueDAO {

    public List<VenueDTO> getAvailableVenues() {
        List<VenueDTO> list = new ArrayList<>();
        String sql = "SELECT venue_id, venue_name, location, status FROM Venue WHERE status = 'AVAILABLE' ORDER BY venue_name";

        try (Connection conn = DBUtils.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                VenueDTO v = new VenueDTO();
                v.setVenueId(rs.getInt("venue_id"));
                v.setVenueName(rs.getNString("venue_name"));
                v.setLocation(rs.getNString("location"));
                v.setStatus(rs.getNString("status"));
                list.add(v);
            }

        } catch (Exception e) {
            System.err.println("[ERROR] getAvailableVenues: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    public boolean createVenue(VenueDTO v) {
        String sql = "INSERT INTO Venue(venue_name, location, status) VALUES(?, ?, ?)";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setNString(1, v.getVenueName());
            ps.setNString(2, v.getLocation());
            ps.setNString(3, v.getStatus() == null ? "AVAILABLE" : v.getStatus());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] createVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateVenue(VenueDTO v) {
        String sql = "UPDATE Venue SET venue_name = ?, location = ? WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setNString(1, v.getVenueName());
            ps.setNString(2, v.getLocation());
            ps.setInt(3, v.getVenueId());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] updateVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean softDeleteVenue(int venueId) {
        String sql = "UPDATE Venue SET status = 'UNAVAILABLE' WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (Exception e) {
            System.err.println("[ERROR] softDeleteVenue: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean existsById(int venueId) {
        String sql = "SELECT 1 FROM Venue WHERE venue_id = ?";
        try (Connection conn = DBUtils.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, venueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] existsById: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
