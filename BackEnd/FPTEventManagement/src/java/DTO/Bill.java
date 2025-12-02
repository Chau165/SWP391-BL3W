package DTO;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Bill {
    private int billId;
    private int userId;

    private BigDecimal totalAmount;
    private String currency;
    private String paymentMethod;
    private String paymentStatus;
    private Timestamp createdAt;
    
    // Constructors
    public Bill() {}
    
    public Bill(int userId, BigDecimal totalAmount, String currency, 
                String paymentMethod, String paymentStatus) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }
    
    // Getters & Setters
    public int getBillId() {
        return billId;
    }
    
    public void setBillId(int billId) {
        this.billId = billId;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    // ❌ REMOVED: getEventId() / setEventId()
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
    
    public String getPaymentStatus() {
        return paymentStatus;
    }
    
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "Bill{" +
                "billId=" + billId +
                ", userId=" + userId +
                ", totalAmount=" + totalAmount +
                ", currency='" + currency + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}