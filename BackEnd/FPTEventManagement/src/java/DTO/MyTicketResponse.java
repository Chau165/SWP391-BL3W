package DTO;

/**
 * ========================================================================================================
 * DTO: MyTicketResponse - RESPONSE TRẢ VỀ THÔNG TIN VÉ CHO SINH VIÊN
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Class này dùng để trả về thông tin vé của sinh viên trong API GET /api/registrations/my-tickets
 * - Chứa các thông tin cơ bản: ID vé, mã QR, tên sự kiện, địa điểm, trạng thái, thời gian checkin/checkout
 * 
 * LUỒNG DỮ LIỆU:
 * 1. TicketDAO.getTicketsByUserId(userId) truy vấn database (JOIN nhiều bảng)
 * 2. Dữ liệu từ ResultSet được map vào MyTicketResponse object
 * 3. List<MyTicketResponse> được chuyển thành JSON qua Gson
 * 4// ==================== GETTERS & SETTERS ====================
    // Các method này cho phép Gson và các framework khác serialize/deserialize JSON

    . JSON được trả về cho Frontend (React/Vue)
 * 
 * MAPPING DATABASE:
 * - ticketId        <- Ticket.ticket_id
 * - ticketCode      <- Ticket.qr_code_value (Base64 image của QR code)
 * - eventName       <- Event.title (JOIN qua Ticket.event_id)
 * - venueName       <- Venue.venue_name (JOIN: Event -> VenueArea -> Venue)
 * - startTime       <- Event.start_time
 * - status          <- Ticket.status (BOOKED, CHECKED_IN, CHECKED_OUT, CANCELLED, EXPIRED)
 * - checkInTime     <- Ticket.checkin_time
 * - checkOutTime    <- Ticket.check_out_time
 * 
 * STATUS FLOW:
 * - BOOKED: Vé đã đăng ký, chưa check-in
 * - CHECKED_IN: Đã quét QR code vào sự kiện
 * - CHECKED_OUT: Đã check-out khỏi sự kiện
 * - CANCELLED: Vé bị hủy
 * - EXPIRED: Vé hết hạn (sau khi sự kiện kết thúc)
 * 
 * SỬ DỤNG:
 * - Controller: controller/MyTicketController.java (doGet method)
 * - DAO: DAO/TicketDAO.java (getTicketsByUserId method)
 * - Frontend: Hiển thị danh sách vé trong màn hình "My Tickets" / "Lịch sử vé"
 */

import java.sql.Timestamp;

public class MyTicketResponse {

    // ID của vé trong database
    private int ticketId;

    // Mã QR code dạng Base64 (để FE hiển thị ảnh QR ngay lập tức)
    private String ticketCode;

    // Tên sự kiện (VD: "Workshop AI 2025", "Music Fest Spring")
    private String eventName;

    // Tên địa điểm tổ chức (VD: "FPT Hòa Lạc", "FPT Arena") - có thể null
    private String venueName; // optional

    // Thời gian bắt đầu sự kiện
    private Timestamp startTime;

    // Trạng thái vé: BOOKED / CHECKED_IN / CHECKED_OUT / CANCELLED / EXPIRED
    private String status;

    // Thời gian check-in vào sự kiện (null nếu chưa check-in)
    private Timestamp checkInTime;

    // Thời gian check-out khỏi sự kiện (null nếu chưa checkout)
    private Timestamp checkOutTime;

    public Timestamp getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(Timestamp checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public String getTicketCode() {
        return ticketCode;
    }

    public void setTicketCode(String ticketCode) {
        this.ticketCode = ticketCode;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(Timestamp checkInTime) {
        this.checkInTime = checkInTime;
    }

}