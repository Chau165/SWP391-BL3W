package DTO;

public class EventStatsDTO {

    private int eventId;
    private int totalRegistered;
    private int totalCheckedIn;
    private String checkInRate; // e.g., "75.00%"

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getTotalRegistered() {
        return totalRegistered;
    }

    public void setTotalRegistered(int totalRegistered) {
        this.totalRegistered = totalRegistered;
    }

    public int getTotalCheckedIn() {
        return totalCheckedIn;
    }

    public void setTotalCheckedIn(int totalCheckedIn) {
        this.totalCheckedIn = totalCheckedIn;
    }

    public String getCheckInRate() {
        return checkInRate;
    }

    public void setCheckInRate(String checkInRate) {
        this.checkInRate = checkInRate;
    }
}
