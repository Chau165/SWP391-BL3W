package DTO;

import java.sql.Timestamp;

/**
 * Dto dùng cho danh sách event FE hiển thị.
 * CỐ Ý không có: createdBy, createdAt, venueId, speakerId
 */
public class EventListDto {
    private int eventId;
    private String title;
    private String description;
    private Timestamp startTime;
    private Timestamp endTime;
    private int maxSeats;
    private String status;

    public EventListDto() {}

    public EventListDto(int eventId, String title, String description,
                        Timestamp startTime, Timestamp endTime,
                        int maxSeats, String status) {
        this.eventId = eventId;
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxSeats = maxSeats;
        this.status = status;
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
}
