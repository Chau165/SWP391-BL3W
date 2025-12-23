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
import java.math.BigDecimal;

public class MyTicketResponse {
    private int ticketId;
    private String ticketCode;      // qr_code_value
    private String eventName;       
    private String venueName;       // <--- THÊM LẠI TRƯỜNG NÀY ĐỂ FIX LỖI
    private Timestamp startTime;    
    private String status;          
    private Timestamp checkInTime;
    private Timestamp checkOutTime;
    private String category;        
    private BigDecimal categoryPrice; 
    private String seatCode;        
    private String buyerName;       
    private Timestamp purchaseDate; 

    public MyTicketResponse() {}

    // Getter/Setter cho venueName (Dùng cho getTicketsByUserId)
    public String getVenueName() { return venueName; }
    public void setVenueName(String venueName) { this.venueName = venueName; }

    // Các Getter/Setter khác
    public int getTicketId() { return ticketId; }
    public void setTicketId(int ticketId) { this.ticketId = ticketId; }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getCheckInTime() { return checkInTime; }
    public void setCheckInTime(Timestamp checkInTime) { this.checkInTime = checkInTime; }

    public Timestamp getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(Timestamp checkOutTime) { this.checkOutTime = checkOutTime; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getCategoryPrice() { return categoryPrice; }
    public void setCategoryPrice(BigDecimal categoryPrice) { this.categoryPrice = categoryPrice; }

    public String getSeatCode() { return seatCode; }
    public void setSeatCode(String seatCode) { this.seatCode = seatCode; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public Timestamp getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Timestamp purchaseDate) { this.purchaseDate = purchaseDate; }
}