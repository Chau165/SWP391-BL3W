package service;

import DAO.VenueDAO;
import DTO.Venue;

import java.util.HashMap;
import java.util.Map;

public class VenueService {

    private final VenueDAO venueDAO = new VenueDAO();

    public Map<String, Object> createVenue(Venue venue) {
        Map<String, Object> result = new HashMap<>();

        if (venue == null || venue.getVenueName() == null || venue.getVenueName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "venue_name is required");
            return result;
        }

        String address = venue.getAddress() == null ? "" : venue.getAddress();
        boolean ok = venueDAO.createVenue(venue.getVenueName(), address);

        if (ok) {
            result.put("success", true);
            result.put("message", "Venue created");
        } else {
            result.put("success", false);
            result.put("message", "Failed to create venue");
        }
        return result;
    }

    public Map<String, Object> updateVenue(Venue venue) {
        Map<String, Object> result = new HashMap<>();

        if (venue == null || venue.getVenueId() == null) {
            result.put("success", false);
            result.put("message", "venue_id is required");
            return result;
        }

        if (venue.getVenueName() == null || venue.getVenueName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "venue_name is required");
            return result;
        }

        if (!venueDAO.existsVenue(venue.getVenueId())) {
            result.put("success", false);
            result.put("message", "venue_id does not exist");
            return result;
        }

        String address = venue.getAddress() == null ? "" : venue.getAddress();
        String status = venue.getStatus() == null ? "AVAILABLE" : venue.getStatus();
        boolean ok = venueDAO.updateVenue(venue.getVenueId(), venue.getVenueName(), address, status);

        if (ok) {
            result.put("success", true);
            result.put("message", "Venue updated");
        } else {
            result.put("success", false);
            result.put("message", "Failed to update venue");
        }
        return result;
    }

    public Map<String, Object> softDeleteVenue(Integer venueId) {
        Map<String, Object> result = new HashMap<>();

        if (venueId == null) {
            result.put("success", false);
            result.put("message", "venue_id is required");
            return result;
        }

        if (!venueDAO.existsVenue(venueId)) {
            result.put("success", false);
            result.put("message", "venue_id does not exist");
            return result;
        }

        boolean ok = venueDAO.softDeleteVenue(venueId);
        if (ok) {
            result.put("success", true);
            result.put("message", "Venue soft-deleted");
        } else {
            result.put("success", false);
            result.put("message", "Failed to delete venue");
        }
        return result;
    }
}
