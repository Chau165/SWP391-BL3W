package DTO;

import java.sql.Timestamp;
import java.util.List;

public class EventDetailDto {

    private int eventId;
    private String title;
    private String description;
    private Timestamp startTime;
    private Timestamp endTime;
    private int maxSeats;
    private String status;
    private String bannerUrl;

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }
    // Venue tổng
    private String venueName;      // từ Venue

    // Area cụ thể trong venue
    private Integer areaId;        // từ Venue_Area
    private String areaName;       // VD: Hội trường A, BE-207...
    private String floor;          // Tầng 1, Tầng 2...
    private Integer areaCapacity;  // sức chứa của area

    // Speaker
    private String speakerName;    // từ Speaker

    // Danh sách loại vé
    private List<CategoryTicket> tickets;

    // ===== GETTER / SETTER =====
    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public void setEndTime(Timestamp endTime) {
        this.endTime = endTime;
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public void setMaxSeats(int maxSeats) {
        this.maxSeats = maxSeats;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public Integer getAreaId() {
        return areaId;
    }

    public void setAreaId(Integer areaId) {
        this.areaId = areaId;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public Integer getAreaCapacity() {
        return areaCapacity;
    }

    public void setAreaCapacity(Integer areaCapacity) {
        this.areaCapacity = areaCapacity;
    }

    public String getSpeakerName() {
        return speakerName;
    }

    public void setSpeakerName(String speakerName) {
        this.speakerName = speakerName;
    }

    public List<CategoryTicket> getTickets() {
        return tickets;
    }

    public void setTickets(List<CategoryTicket> tickets) {
        this.tickets = tickets;
    }
}
