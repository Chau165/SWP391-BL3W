package DTO;

/**
 * ========================================================================================================
 * DTO: EventStatsDTO - THỐNG KÊ SỰ KIỆN
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Chứa thông tin thống kê của 1 sự kiện
 * - Dùng làm response cho API /api/events/stats
 * - Hiển thị dashboard ORGANIZER/STAFF
 * - Phân tích hiệu quả sự kiện
 * 
 * FIELDS:
 * 1. eventId (int): ID của sự kiện
 * - Khóa ngoại -> Events table
 * - Duy nhất cho mỗi event
 * 
 * 2. totalRegistered (int): Tổng số người đăng ký
 * - COUNT(registrationId) FROM Registrations WHERE eventId
 * - Bao gồm tất cả trạng thái: CONFIRMED, CHECKED_IN, CHECKED_OUT
 * - Không tính CANCELLED
 * 
 * 3. totalCheckedIn (int): Số người đã check-in
 * - COUNT WHERE status = "CHECKED_IN" OR "CHECKED_OUT"
 * - User đã check-in thì vẫn tính dù check-out rồi
 * - Dùng để đo attendance rate
 * 
 * 4. totalCheckedOut (int): Số người đã check-out
 * - COUNT WHERE status = "CHECKED_OUT"
 * - User rời khỏi sự kiện
 * - Dùng để đo engagement rate
 * 
 * 5. checkInRate (double): Tỉ lệ check-in (%)
 * - Công thức: (totalCheckedIn / totalRegistered) * 100
 * - Ví dụ: 350 check-in / 500 registered = 70%
 * - Hiển thị: "70.0%" hoặc "70%"
 * - Đo lường người thực sự tham dự (so với đăng ký)
 * 
 * 6. checkOutRate (double): Tỉ lệ check-out (%)
 * - Công thức: (totalCheckedOut / totalRegistered) * 100
 * - Ví dụ: 200 check-out / 500 registered = 40%
 * - Đo lường người tham gia đến cuối sự kiện
 * 
 * KHỚI TẠO:
 * - Constructor: new EventStatsDTO(eventId, totalRegistered, totalCheckedIn,
 * totalCheckedOut, checkInRate, checkOutRate)
 * - Getters/Setters: Đầy đủ cho tất cả fields
 * - toString(): Hiển thị thông tin thống kê
 * 
 * EXAMPLE DATA:
 * {
 * "eventId": 123,
 * "totalRegistered": 500,
 * "totalCheckedIn": 350,
 * "totalCheckedOut": 200,
 * "checkInRate": 70.0,
 * "checkOutRate": 40.0
 * }
 * 
 * PHÂN TÍCH:
 * - checkInRate = 70% -> Tốt, 70% người đăng ký thực sự tham dự
 * - checkOutRate = 40% -> Chỉ 40% tham gia đến cuối (có thể sự kiện dài, nội
 * dung chưa hấp dẫn)
 * - totalRegistered - totalCheckedIn = 150 no-shows (không đến)
 * - totalCheckedIn - totalCheckedOut = 150 left early (về sớm)
 * 
 * USE CASES:
 * 1. Dashboard ORGANIZER:
 * - Hiển thị các chart: donut chart (attendance), line chart (check-in over
 * time)
 * - So sánh giữa các sự kiện
 * - Đánh giá hiệu quả marketing
 * 
 * 2. Báo cáo sự kiện:
 * - Xuất PDF với số liệu
 * - Gửi email cho stakeholders
 * 
 * 3. Real-time monitoring:
 * - Cập nhật số liệu trong suốt sự kiện
 * - Hiển thị trên màn hình lớn
 * 
 * NÂNG CẤP ĐỀ XUẤT:
 * - Thêm thống kê theo ticket category (VIP vs Standard)
 * - Thêm timestamp: checkInTimes, checkOutTimes (phân bố theo giờ)
 * - Thêm demographics: age, gender distribution
 * - Thêm engagement metrics: feedback rating, survey response rate
 * 
 * KẾT NỐI FILE:
 * - Controller: controller/EventStatsController.java (trả về DTO này)
 * - DAO: DAO/TicketDAO.java (tính toán thống kê từ Registrations table)
 * - Frontend: Hiển thị trong dashboard, charts (Chart.js, D3.js...)
 */

public class EventStatsDTO {

    private int eventId;
    private int totalRegistered;
    private int totalCheckedIn;
    private int totalCheckedOut;
    private String checkInRate; //
    private String checkOutRate;

    public int getEventId() {
        return eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public int getTotalRegistered() {
        return totalRegistered;
    }

    public void setTotalRegistered(int totalRegistered) {
        this.totalRegistered = totalRegistered;
    }

    public int getTotalCheckedIn() {
        return totalCheckedIn;
    }

    public void setTotalCheckedIn(int totalCheckedIn) {
        this.totalCheckedIn = totalCheckedIn;
    }

    public int getTotalCheckedOut() {
        return totalCheckedOut;
    }

    public void setTotalCheckedOut(int totalCheckedOut) {
        this.totalCheckedOut = totalCheckedOut;
    }

    public String getCheckInRate() {
        return checkInRate;
    }

    public void setCheckInRate(String checkInRate) {
        this.checkInRate = checkInRate;
    }

    public String getCheckOutRate() {
        return checkOutRate;
    }

    public void setCheckOutRate(String checkOutRate) {
        this.checkOutRate = checkOutRate;
    }
}