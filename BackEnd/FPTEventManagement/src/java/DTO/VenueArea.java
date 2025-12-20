package DTO;

/**
 * ========================================================================================================
 * DTO: VenueArea - KHU VỰC TRONG ĐỊA ĐIỂM
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Ánh xạ bảng Venue_Area trong SQL Server
 * - Chứa thông tin khu vực cụ thể trong venue (Hall A, Room 201...)
 * - Mỗi area có capacity (sức chứa) và danh sách ghế (Seat)
 * 
 * DATABASE TABLE: Venue_Area
 * Columns:
 * - area_id (INT, PK, IDENTITY): ID khu vực
 * - venue_id (INT, FK -> Venue, NOT NULL): Thuộc venue nào
 * - area_name (NVARCHAR(255), NOT NULL): Tên khu vực
 * - floor (NVARCHAR(50)): Tầng (Floor 1, Floor 2...)
 * - capacity (INT): Sức chứa tối đa
 * - status (VARCHAR(50)): AVAILABLE, UNAVAILABLE
 * 
 * RELATIONSHIPS:
 * - VenueArea N:1 Venue (mỗi area thuộc 1 venue)
 * - VenueArea 1:N Seat (mỗi area có nhiều ghế vật lý)
 * - VenueArea 1:N Event (mỗi area có thể tổ chức nhiều event)
 * 
 * FIELDS:
 * 1. areaId (Integer): ID khu vực (PK)
 * 2. venueId (Integer): ID venue chứa area này (FK)
 * 3. areaName (String): Tên khu vực
 * - Ví dụ: "Hall A", "Room 201", "Auditorium"
 * 4. floor (String): Tầng
 * - Ví dụ: "Floor 1", "Floor 2", "Ground Floor"
 * 5. capacity (Integer): Sức chứa tối đa (số người)
 * - Ví dụ: 500 người cho Hall A
 * - Dùng để tự động generate ghế vật lý (SeatDAO.generateSeatsForArea)
 * 6. status (String): Trạng thái
 * - AVAILABLE: Có thể đặt event
 * - UNAVAILABLE: Không sử dụng
 * 
 * GHẾ VẬT LÝ (SEAT):
 * - Khi tạo VenueArea mới, SeatDAO tự động generate ghế vật lý
 * - Số ghế = capacity
 * - Format ghế: A1, A2, ..., A10, B1, B2, ...
 * - 10 ghế / hàng (seatsPerRow = 10)
 * - INSERT INTO Seat (seat_code, row_no, col_no, status, area_id)
 * 
 * BOOKING SYSTEM:
 * - Mỗi Event chỉ đặt 1 area tại 1 thời điểm
 * - Event liên kết với area qua area_id
 * - Kiểm tra trùng lịch: GetFreeAreasController (1h buffer)
 * - Status của area không đổi khi có event, chỉ kiểm tra overlap time
 * 
 * EXAMPLE DATA:
 * {
 * "areaId": 101,
 * "venueId": 1,
 * "areaName": "Hall A",
 * "floor": "Floor 1",
 * "capacity": 500,
 * "status": "AVAILABLE"
 * }
 * 
 * USE CASES:
 * - ORGANIZER chọn area khi tạo event
 * - Hiển thị danh sách area trống theo time slot
 * - Tự động generate ghế vật lý khi tạo area
 * - Kiểm tra capacity trước khi đặt event
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/VenueAreaDAO.java
 * - DAO: DAO/SeatDAO.java (generate seats)
 * - Controller: controller/VenueAreaController.java
 * - Controller: controller/GetFreeAreasController.java
 * - Service: service/VenueAreaService.java
 * - Parent: DTO/Venue.java
 */

public class VenueArea {
    private Integer areaId;
    private Integer venueId;
    private String areaName;
    private String floor;
    private Integer capacity;
    private String status;

    public Integer getAreaId() {
        return areaId;
    }

    public void setAreaId(Integer areaId) {
        this.areaId = areaId;
    }

    public Integer getVenueId() {
        return venueId;
    }

    public void setVenueId(Integer venueId) {
        this.venueId = venueId;
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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}