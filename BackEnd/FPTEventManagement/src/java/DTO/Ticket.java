package DTO;

import java.sql.Timestamp;

public class Ticket{

    private int ticketId;
    private int eventId;
    private int userId;
    private int categoryTicketId;
    private Integer billId;     // nullable nếu vé free
    private Integer seatId;     // nullable nếu không chọn chỗ
    private String qrCodeValue;
    private Timestamp qrIssuedAt;
    private String status;      // BOOKED / CHECKED_IN / CANCELLED / EXPIRED
    private Timestamp checkinTime;

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getCategoryTicketId() {
        return categoryTicketId;
    }

    public void setCategoryTicketId(int categoryTicketId) {
        this.categoryTicketId = categoryTicketId;
    }

    public Integer getBillId() {
        return billId;
    }

    public void setBillId(Integer billId) {
        this.billId = billId;
    }

    public Integer getSeatId() {
        return seatId;
    }

    public void setSeatId(Integer seatId) {
        this.seatId = seatId;
    }

    public String getQrCodeValue() {
        return qrCodeValue;
    }

    public void setQrCodeValue(String qrCodeValue) {
        this.qrCodeValue = qrCodeValue;
    }

    public Timestamp getQrIssuedAt() {
        return qrIssuedAt;
    }

    public void setQrIssuedAt(Timestamp qrIssuedAt) {
        this.qrIssuedAt = qrIssuedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCheckinTime() {
        return checkinTime;
    }

    public void setCheckinTime(Timestamp checkinTime) {
        this.checkinTime = checkinTime;
    }
}
