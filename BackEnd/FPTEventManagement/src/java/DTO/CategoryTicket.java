package DTO;

import java.math.BigDecimal;

public class CategoryTicket {

    private int categoryTicketId;
    private int eventId;
    private String name;          // VIP / STANDARD
    private String description;
    private BigDecimal price;
    private Integer maxQuantity;  // nullable
    private String status;        // ACTIVE / INACTIVE

    public int getCategoryTicketId() {
        return categoryTicketId;
    }

    public void setCategoryTicketId(int categoryTicketId) {
        this.categoryTicketId = categoryTicketId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(Integer maxQuantity) {
        this.maxQuantity = maxQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
