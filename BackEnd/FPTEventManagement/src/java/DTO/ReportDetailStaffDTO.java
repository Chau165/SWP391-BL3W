package DTO;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ReportDetailStaffDTO {
    private int reportId;
    private int ticketId;

    private String title;
    private String description;
    private String imageUrl;

    private Timestamp createdAt;
    private String reportStatus;

    private int studentId;
    private String studentName;

    private String ticketStatus;

    private int categoryTicketId;
    private String categoryTicketName;
    private BigDecimal price;

    // Getters & Setters
    public int getReportId() { return reportId; }
    public void setReportId(int reportId) { this.reportId = reportId; }

    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }

    public int getCategoryTicketId() { return categoryTicketId; }
    public void setCategoryTicketId(int categoryTicketId) { this.categoryTicketId = categoryTicketId; }

    public String getCategoryTicketName() { return categoryTicketName; }
    public void setCategoryTicketName(String categoryTicketName) { this.categoryTicketName = categoryTicketName; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
