package DTO;

public class EventStatsResponse {
    private int eventId;
    private int totalRegistered; // Tổng vé (trừ Cancelled/Expired)
    
    private int totalBooking;
    private String bookingRate;
    
    private int totalCheckedIn;
    private String checkInRate;
    
    private int totalCheckedOut;
    private String checkOutRate;
    
    private int totalRefunded;
    private String refundedRate;

    public EventStatsResponse() {
    }

    // --- GETTERS AND SETTERS ---
    public int getEventId() { return eventId; }
    public void setEventId(int eventId) { this.eventId = eventId; }

    public int getTotalRegistered() { return totalRegistered; }
    public void setTotalRegistered(int totalRegistered) { this.totalRegistered = totalRegistered; }

    public int getTotalBooking() { return totalBooking; }
    public void setTotalBooking(int totalBooking) { this.totalBooking = totalBooking; }

    public String getBookingRate() { return bookingRate; }
    public void setBookingRate(String bookingRate) { this.bookingRate = bookingRate; }

    public int getTotalCheckedIn() { return totalCheckedIn; }
    public void setTotalCheckedIn(int totalCheckedIn) { this.totalCheckedIn = totalCheckedIn; }

    public String getCheckInRate() { return checkInRate; }
    public void setCheckInRate(String checkInRate) { this.checkInRate = checkInRate; }

    public int getTotalCheckedOut() { return totalCheckedOut; }
    public void setTotalCheckedOut(int totalCheckedOut) { this.totalCheckedOut = totalCheckedOut; }

    public String getCheckOutRate() { return checkOutRate; }
    public void setCheckOutRate(String checkOutRate) { this.checkOutRate = checkOutRate; }

    public int getTotalRefunded() { return totalRefunded; }
    public void setTotalRefunded(int totalRefunded) { this.totalRefunded = totalRefunded; }

    public String getRefundedRate() { return refundedRate; }
    public void setRefundedRate(String refundedRate) { this.refundedRate = refundedRate; }
}