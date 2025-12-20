package DTO;

/**
 * ========================================================================================================
 * DTO: Venue - ĐỊA ĐIỂM TỔ CHỨC SỰ KIỆN
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Ánh xạ bảng Venue trong SQL Server
 * - Chứa thông tin địa điểm tổ chức sự kiện (trường học, trung tâm hội nghị...)
 * - Nested areas: Mỗi Venue có nhiều Venue_Area (phòng, hội trường...)
 * 
 * DATABASE TABLE: Venue
 * Columns:
 * - venue_id (INT, PK, IDENTITY): ID địa điểm
 * - venue_name (NVARCHAR(255), NOT NULL): Tên địa điểm
 * - location (NVARCHAR(MAX)): Địa chỉ đầy đủ
 * - status (VARCHAR(50)): AVAILABLE, UNAVAILABLE
 * 
 * RELATIONSHIPS:
 * - Venue 1:N Venue_Area (mỗi venue có nhiều khu vực)
 * - Venue_Area 1:N Event (mỗi area có thể tổ chức nhiều event)
 * 
 * FIELDS:
 * 1. venueId (Integer): ID địa điểm (PK)
 * 2. venueName (String): Tên địa điểm
 *    - Ví dụ: "FPT University HCM", "SECC Conference Center"
 * 3. address (String): Địa chỉ (DB column: location)
 *    - Ví dụ: "Lô E2a-7, Đường D1, Quận 9, TP.HCM"
 * 4. status (String): Trạng thái
 *    - AVAILABLE: Đang hoạt động, có thể đặt event
 *    - UNAVAILABLE: Ngừng hoạt động, không hiển thị
 * 5. areas (List<VenueArea>): Danh sách các khu vực trong venue
 *    - Nested list cho GET /api/venues
 *    - Lấy qua LEFT JOIN Venue_Area
 * 
 * MAPPING NOTE:
 * - Field "address" trong Java được map với column "location" trong DB
 * - VenueDAO sử dụng rs.getString("location") -> venue.setAddress()
 * 
 * NESTED STRUCTURE:
 * {
 *   "venueId": 1,
 *   "venueName": "FPT University",
 *   "address": "Lô E2a-7, Đường D1, Quận 9",
 *   "status": "AVAILABLE",
 *   "areas": [
 *     { "areaId": 101, "areaName": "Hall A", "capacity": 500, ... },
 *     { "areaId": 102, "areaName": "Room 201", "capacity": 100, ... }
 *   ]
 * }
 * 
 * USE CASES:
 * - ORGANIZER chọn venue khi tạo event
 * - Hiển thị dropdown venue và areas
 * - Admin quản lý danh sách địa điểm
 * - Kiểm tra venue còn trống theo thời gian (GetFreeAreasController)
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/VenueDAO.java
 * - DTO: DTO/VenueArea.java (nested)
 * - Controller: controller/VenueController.java
 * - Service: service/VenueService.java
 */

import java.util.List;

public class Venue {
    private Integer venueId;
    private String venueName;
    private String address;
    private String status;
    private List<VenueArea> areas; // Nested areas for GET /api/venues

    public Integer getVenueId() {
        return venueId;
    }

    public void setVenueId(Integer venueId) {
        this.venueId = venueId;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<VenueArea> getAreas() {
        return areas;
    }

    public void setAreas(List<VenueArea> areas) {
        this.areas = areas;
    }
}