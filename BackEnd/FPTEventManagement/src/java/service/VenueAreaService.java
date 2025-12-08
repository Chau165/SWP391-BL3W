package service;

import DAO.VenueAreaDAO;
import DTO.VenueArea;

import java.util.HashMap;
import java.util.Map;

public class VenueAreaService {

    private final VenueAreaDAO venueAreaDAO = new VenueAreaDAO();

    public Map<String, Object> createArea(VenueArea area) {
        Map<String, Object> result = new HashMap<>();

        if (area == null || area.getVenueId() == null) {
            result.put("success", false);
            result.put("message", "venue_id is required");
            return result;
        }

        if (area.getAreaName() == null || area.getAreaName().trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "area_name is required");
            return result;
        }

        if (area.getCapacity() == null || area.getCapacity() <= 0) {
            result.put("success", false);
            result.put("message", "capacity must be greater than 0");
            return result;
        }

        String floor = area.getFloor() == null ? "" : area.getFloor();

        try {
            // 👇 Gọi DAO trả về area_id mới tạo
            int newAreaId = venueAreaDAO.createArea(
                    area.getVenueId(),
                    area.getAreaName(),
                    floor,
                    area.getCapacity()
            );

            if (newAreaId > 0) {
                result.put("success", true);
                result.put("message", "Area created");
                result.put("areaId", newAreaId); // 👈 rất quan trọng: controller dùng để generate seat
            } else {
                result.put("success", false);
                result.put("message", "Failed to create area");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "Error when creating area: " + e.getMessage());
        }

        return result;
    }

    public Map<String, Object> updateArea(VenueArea area) {
        Map<String, Object> result = new HashMap<>();

        if (area == null || area.getAreaId() == null) {
            result.put("success", false);
            result.put("message", "area_id is required");
            return result;
        }

        if (area.getCapacity() == null || area.getCapacity() <= 0) {
            result.put("success", false);
            result.put("message", "capacity must be greater than 0");
            return result;
        }

        int areaId = area.getAreaId();

        if (!venueAreaDAO.existsArea(areaId)) {
            result.put("success", false);
            result.put("message", "area_id does not exist");
            return result;
        }

        // CRITICAL FIX: Pass status to DAO (default to AVAILABLE if not provided)
        String status = area.getStatus();
        if (status == null || status.trim().isEmpty()) {
            status = "AVAILABLE";
        }

        boolean ok = venueAreaDAO.updateArea(areaId,
                area.getAreaName() == null ? "" : area.getAreaName(),
                area.getFloor() == null ? "" : area.getFloor(),
                area.getCapacity(),
                status);

        if (ok) {
            result.put("success", true);
            result.put("message", "Area updated");
        } else {
            result.put("success", false);
            result.put("message", "Failed to update area");
        }
        return result;
    }

    public Map<String, Object> softDeleteArea(Integer areaId) {
        Map<String, Object> result = new HashMap<>();
        if (areaId == null) {
            result.put("success", false);
            result.put("message", "area_id is required");
            return result;
        }

        if (!venueAreaDAO.existsArea(areaId)) {
            result.put("success", false);
            result.put("message", "area_id does not exist");
            return result;
        }

        boolean ok = venueAreaDAO.softDeleteArea(areaId);
        if (ok) {
            result.put("success", true);
            result.put("message", "Area soft-deleted");
        } else {
            result.put("success", false);
            result.put("message", "Failed to delete area");
        }
        return result;
    }
}
