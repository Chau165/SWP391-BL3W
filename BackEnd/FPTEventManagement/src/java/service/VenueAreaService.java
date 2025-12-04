package service;

import DAO.VenueAreaDAO;
import DTO.VenueArea;
import utils.JwtUtils;

import java.util.List;

public class VenueAreaService {
    private final VenueAreaDAO areaDAO = new VenueAreaDAO();
    private final VenueService venueService = new VenueService();

    public List<VenueArea> getAvailableAreasByVenueId(String token, int venueId) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return null;
        role = role.toUpperCase();
        if (!(role.equals("ORGANIZER") || role.equals("ADMIN") || role.equals("STAFF"))) {
            return null;
        }
        return areaDAO.getAvailableAreasByVenueId(venueId);
    }

    public boolean createArea(String token, VenueArea va) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;

        // Validation
        if (va.getVenueId() == null) return false;
        if (!venueService.venueExists(va.getVenueId())) return false;
        if (va.getCapacity() == null || va.getCapacity() <= 0) return false;
        if (va.getAreaName() == null || va.getAreaName().trim().isEmpty()) return false;

        va.setStatus("AVAILABLE");
        return areaDAO.createArea(va);
    }

    public boolean updateArea(String token, VenueArea va) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;

        if (va.getAreaId() == null) return false;
        if (va.getCapacity() == null || va.getCapacity() <= 0) return false;
        if (va.getAreaName() == null || va.getAreaName().trim().isEmpty()) return false;

        // Validate area exists
        if (!areaDAO.areaExists(va.getAreaId())) return false;

        return areaDAO.updateArea(va);
    }

    public boolean softDeleteArea(String token, int areaId) {
        String role = JwtUtils.getRoleFromToken(token);
        if (role == null) return false;
        role = role.toUpperCase();
        if (!(role.equals("ADMIN") || role.equals("ORGANIZER"))) return false;
        // Validate area exists
        if (!areaDAO.areaExists(areaId)) return false;
        return areaDAO.softDeleteArea(areaId);
    }
}
