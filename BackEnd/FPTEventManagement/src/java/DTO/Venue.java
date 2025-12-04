package DTO;

import java.util.List;

public class Venue {
    private Integer venueId;
    private String venueName;
    private String address;
    private String status;
    private List<VenueArea> areas;  // Nested areas for GET /api/venues

    public Integer getVenueId() {
        return venueId;
    }

    public void setVenueId(Integer venueId) {
        this.venueId = venueId;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<VenueArea> getAreas() {
        return areas;
    }

    public void setAreas(List<VenueArea> areas) {
        this.areas = areas;
    }
}
