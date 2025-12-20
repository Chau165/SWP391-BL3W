package DTO;

/**
 * ========================================================================================================
 * DTO: Ticket - ENTITY ĐẠI DIỆN CHO BẢNG TICKET TRONG DATABASE
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Class này ánh xạ (mapping) với bảng Ticket trong SQL Server
 * - Lưu trữ thông tin chi tiết về 1 vé sự kiện đã được đăng ký/mua
 * - Sử dụng trong các thao tác CRUD với bảng Ticket thông qua TicketDAO
 * 
 * CẤU TRÚC BẢNG DATABASE (Ticket):
 * - ticket_id: INT IDENTITY PRIMARY KEY
 * - event_id: INT NOT NULL (FK -> Event.event_id)
 * - user_id: INT NOT NULL (FK -> Users.user_id)
 * - category_ticket_id: INT NOT NULL (FK -> CategoryTicket.category_ticket_id)
 * - bill_id: INT NULL (FK -> Bill.bill_id, null nếu vé miễn phí)
 * - seat_id: INT NULL (FK -> Seat.seat_id, null nếu không chọn chỗ)
 * - qr_code_value: NVARCHAR(MAX) NOT NULL (Base64 image của QR code)
 * - qr_issued_at: DATETIME2 DEFAULT GETDATE()
 * - status: NVARCHAR(20) NOT NULL (BOOKED/CHECKED_IN/CHECKED_OUT/CANCELLED/EXPIRED)
 * - checkin_time: DATETIME2 NULL
 * - check_out_time: DATETIME2 NULL (thêm mới cho tính năng checkout)
 * 
 * QUAN HỆ DATABASE:
 * - Ticket N --- 1 Event (nhiều vé thuộc 1 sự kiện)
 * - Ticket N --- 1 Users (nhiều vé thuộc 1 người dùng)
 * - Ticket N --- 1 CategoryTicket (nhiều vé cùng loại: VIP, Regular, Free)
 * - Ticket N --- 1 Seat (nhiều vé có thể cùng ghế nếu nhiều ngày hoặc session)
 * - Ticket N --- 1 Bill (nhiều vé trong 1 đơn thanh toán)
 * 
 * LUỒNG DỮ LIỆU:
 * 1. Frontend gửi request đăng ký vé (POST /api/registrations/register)
 * 2. Backend tạo Ticket object với status = "BOOKED"
 * 3. TicketDAO.insertTicket() insert vào database
 * 4. Sinh QR code từ ticketId và lưu vào qr_code_value
 * 5. Khi user check-in: Update status = "CHECKED_IN", set checkin_time
 * 6. Khi user check-out: Update status = "CHECKED_OUT", set check_out_time
 * 
 * STATUS LIFECYCLE:
 * BOOKED -> CHECKED_IN -> CHECKED_OUT
 *    |         |              |
 *    v         v              v
 * CANCELLED  EXPIRED      EXPIRED
 * 
 * SỬ DỤNG:
 * - DAO: DAO/TicketDAO.java (insert, update, query tickets)
 * - Controller: controller/RegistrationController.java, controller/CheckinController.java
 * - Service: Tạo QR code, quản lý vé, thống kê checkin
 */

import java.sql.Timestamp;

public class Ticket {

    // ID duy nhất của vé (auto-increment trong database)
    private int ticketId;

    // ID sự kiện mà vé này thuộc về
    private int eventId;

    // ID người dùng đăng ký vé này
    private int userId;

    // ID loại vé (VIP, Regular, Standard, Free...)
    private int categoryTicketId;

    // ID hóa đơn thanh toán (nullable - null nếu vé miễn phí)
    private Integer billId; // nullable nếu vé free

    // ID ghế ngồi (nullable - null nếu không chọn chỗ ngồi cụ thể)
    private Integer seatId; // nullable nếu không chọn chỗ

    // Giá trị QR code dạng Base64 (ảnh PNG được encode thành string)
    // Frontend decode và hiển thị ảnh để scan
    private String qrCodeValue;

    // Thời điểm QR code được tạo
    private Timestamp qrIssuedAt;

    // Trạng thái vé: BOOKED / CHECKED_IN / CHECKED_OUT / CANCELLED / EXPIRED
    private String status; // BOOKED / CHECKED_IN / CANCELLED / EXPIRED

    // Thời gian check-in thực tế (quét QR tại cổng vào sự kiện)
    private Timestamp checkinTime;

    // Thời gian check-out (quét QR tại cổng ra hoặc tự động sau khi sự kiện kết
    // thúc)
    private Timestamp checkoutTime;

    // ==================== GETTERS & SETTERS ====================
    // Các method này cho phép truy cập và thay đổi giá trị các field
    // Cần thiết cho các framework ORM, JSON serialization, và business logic

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getCategoryTicketId() {
        return categoryTicketId;
    }

    public void setCategoryTicketId(int categoryTicketId) {
        this.categoryTicketId = categoryTicketId;
    }

    public Integer getBillId() {
        return billId;
    }

    public void setBillId(Integer billId) {
        this.billId = billId;
    }

    public Integer getSeatId() {
        return seatId;
    }

    public void setSeatId(Integer seatId) {
        this.seatId = seatId;
    }

    public String getQrCodeValue() {
        return qrCodeValue;
    }

    public void setQrCodeValue(String qrCodeValue) {
        this.qrCodeValue = qrCodeValue;
    }

    public Timestamp getQrIssuedAt() {
        return qrIssuedAt;
    }

    public void setQrIssuedAt(Timestamp qrIssuedAt) {
        this.qrIssuedAt = qrIssuedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCheckinTime() {
        return checkinTime;
    }

    public void setCheckinTime(Timestamp checkinTime) {
        this.checkinTime = checkinTime;
    }

    public Timestamp getCheckoutTime() {
        return checkoutTime;
    }

    public void setCheckoutTime(Timestamp checkoutTime) {
        this.checkoutTime = checkoutTime;
    }
}