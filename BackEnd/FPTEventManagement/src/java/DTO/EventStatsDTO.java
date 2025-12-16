package DTO;

public class EventStatsDTO {

    private int eventId;
    private int totalRegistered;
    private int totalCheckedIn;
    private int totalCheckedOut;
    private String checkInRate; //
    private String checkOutRate;

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

    public int getTotalCheckedOut() {
        return totalCheckedOut;
    }

    public void setTotalCheckedOut(int totalCheckedOut) {
        this.totalCheckedOut = totalCheckedOut;
    }

    public String getCheckInRate() {
        return checkInRate;
    }

    public void setCheckInRate(String checkInRate) {
        this.checkInRate = checkInRate;
    }

    public String getCheckOutRate() {
        return checkOutRate;
    }

    public void setCheckOutRate(String checkOutRate) {
        this.checkOutRate = checkOutRate;
    }
}