package DTO;

import java.sql.Timestamp;
import java.math.BigDecimal;

public class MyTicketResponse {
    private int ticketId;
    private String ticketCode;      // qr_code_value
    private String eventName;       
    private String venueName;       // <--- THÊM LẠI TRƯỜNG NÀY ĐỂ FIX LỖI
    private Timestamp startTime;    
    private String status;          
    private Timestamp checkInTime;
    private Timestamp checkOutTime;
    private String category;        
    private BigDecimal categoryPrice; 
    private String seatCode;        
    private String buyerName;       
    private Timestamp purchaseDate; 

    public MyTicketResponse() {}

    // Getter/Setter cho venueName (Dùng cho getTicketsByUserId)
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    // Các Getter/Setter khác
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCheckInTime() { return checkInTime; }
    public void setCheckInTime(Timestamp checkInTime) { this.checkInTime = checkInTime; }

    public Timestamp getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(Timestamp checkOutTime) { this.checkOutTime = checkOutTime; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getCategoryPrice() { return categoryPrice; }
    public void setCategoryPrice(BigDecimal categoryPrice) { this.categoryPrice = categoryPrice; }

    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public Timestamp getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Timestamp purchaseDate) { this.purchaseDate = purchaseDate; }
}