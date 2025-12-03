package service;

import DAO.VenueDAO;
import DTO.VenueDTO;
import utils.JwtUtils;

import java.util.List;

public class VenueService {
    private final VenueDAO venueDAO = new VenueDAO();

    public List<VenueDTO> getAvailableVenues(String token) {
        // token already validated by filter; still check role
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return null;
        role = role.toUpperCase();
        if (!(role.equals("ORGANIZER") || role.equals("ADMIN") || role.equals("STAFF") || role.equals("STUDENT"))) {
            return null;
        }
        return venueDAO.getAvailableVenues();
    }

    public boolean createVenue(String token, VenueDTO v) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;
        if (v.getVenueName() == null || v.getVenueName().trim().isEmpty()) return false;
        v.setStatus("AVAILABLE");
        return venueDAO.createVenue(v);
    }

    public boolean updateVenue(String token, VenueDTO v) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;
        if (v.getVenueId() == null) return false;
        return venueDAO.updateVenue(v);
    }

    public boolean softDeleteVenue(String token, int venueId) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;
        return venueDAO.softDeleteVenue(venueId);
    }

    public boolean venueExists(int venueId) {
        return venueDAO.existsById(venueId);
    }
}
