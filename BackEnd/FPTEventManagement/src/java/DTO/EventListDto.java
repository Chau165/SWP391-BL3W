package DTO;

import java.sql.Timestamp;

/**
 * Dto dùng cho danh sách event FE hiển thị.
 * CỐ Ý không có: createdBy, createdAt, speakerId
 * ĐÃ THÊM: thông tin khu vực & địa điểm (areaName, floor, venueName, venueLocation)
 */
public class EventListDto {
    private int eventId;
    private String title;
    private String description;
    private Timestamp startTime;
    private Timestamp endTime;
    private int maxSeats;
    private String status;
    private String bannerUrl;

    // ===== Thông tin khu vực =====
    private Integer areaId;
    private String areaName;
    private String floor;

    // ===== Thông tin địa điểm =====
    private String venueName;
    private String venueLocation;

    public EventListDto() {}

    public EventListDto(int eventId, String title, String description,
                        Timestamp startTime, Timestamp endTime,
                        int maxSeats, String status, String bannerUrl) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxSeats = maxSeats;
        this.status = status;
        this.bannerUrl = bannerUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

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

    // ===== Getter / Setter khu vực =====
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

    // ===== Getter / Setter địa điểm =====
    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getVenueLocation() {
        return venueLocation;
    }

    public void setVenueLocation(String venueLocation) {
        this.venueLocation = venueLocation;
    }
}
