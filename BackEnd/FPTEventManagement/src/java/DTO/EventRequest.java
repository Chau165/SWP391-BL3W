package DTO;

import java.sql.Timestamp;

public class EventRequest {
    private Integer requestId;
    private Integer requesterId;
    private String requesterName;   // tên người tạo request (Organizer)
    private String title;
    private String description;
    private Timestamp preferredStartTime;
    private Timestamp preferredEndTime;
    private Integer expectedCapacity;
    private String status;
    private Timestamp createdAt;

    private Integer processedBy;      // id người duyệt (Staff/Admin)
    private String processedByName;   // ✅ tên người duyệt
    private Timestamp processedAt;
    private String organizerNote;
    private Integer createdEventId;

    public Integer getRequestId() { return requestId; }
    public void setRequestId(Integer requestId) { this.requestId = requestId; }

    public Integer getRequesterId() { return requesterId; }
    public void setRequesterId(Integer requesterId) { this.requesterId = requesterId; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getPreferredStartTime() { return preferredStartTime; }
    public void setPreferredStartTime(Timestamp preferredStartTime) { this.preferredStartTime = preferredStartTime; }

    public Timestamp getPreferredEndTime() { return preferredEndTime; }
    public void setPreferredEndTime(Timestamp preferredEndTime) { this.preferredEndTime = preferredEndTime; }

    public Integer getExpectedCapacity() { return expectedCapacity; }
    public void setExpectedCapacity(Integer expectedCapacity) { this.expectedCapacity = expectedCapacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Integer getProcessedBy() { return processedBy; }
    public void setProcessedBy(Integer processedBy) { this.processedBy = processedBy; }

    public String getProcessedByName() { return processedByName; }
    public void setProcessedByName(String processedByName) { this.processedByName = processedByName; }

    public Timestamp getProcessedAt() { return processedAt; }
    public void setProcessedAt(Timestamp processedAt) { this.processedAt = processedAt; }

    public String getOrganizerNote() { return organizerNote; }
    public void setOrganizerNote(String organizerNote) { this.organizerNote = organizerNote; }

    public Integer getCreatedEventId() { return createdEventId; }
    public void setCreatedEventId(Integer createdEventId) { this.createdEventId = createdEventId; }
}
