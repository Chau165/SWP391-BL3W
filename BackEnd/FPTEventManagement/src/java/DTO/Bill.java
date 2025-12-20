package DTO;

/**
 * ========================================================================================================
 * DTO: Bill - ENTITY HÓA ĐƠN (BILL TABLE)
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Ánh xạ bảng Bill trong SQL Server
 * - Quản lý thông tin hóa đơn thanh toán của user
 * - 1 Bill chứa nhiều Tickets (1-n relationship)
 * 
 * DATABASE TABLE: Bill
 * Columns:
 * - bill_id (INT, PK, IDENTITY): ID hóa đơn tự tăng
 * - user_id (INT, FK -> Users, NOT NULL): Người mua
 * - total_amount (DECIMAL(18,2)): Tổng tiền
 * - currency (VARCHAR(10)): Đơn vị tiền tệ (VND)
 * - payment_method (VARCHAR(50)): Phương thức (VNPAY, CASH...)
 * - payment_status (VARCHAR(50)): Trạng thái (PAID, PENDING, FAILED)
 * - created_at (DATETIME): Thời điểm tạo
 * 
 * RELATIONSHIPS:
 * - Bill N:1 Users (mỗi bill thuộc 1 user)
 * - Bill 1:N Ticket (1 bill có nhiều vé)
 * 
 * FIELDS:
 * 1. billId (int): ID hóa đơn, auto-generated khi INSERT
 * 2. userId (int): ID user mua vé (FK -> Users.user_id)
 * 3. totalAmount (BigDecimal): Tổng số tiền (ví dụ: 500000.00 VND)
 * 4. currency (String): Đơn vị tiền tệ (mặc định "VND")
 * 5. paymentMethod (String): Phương thức thanh toán
 *    - VNPAY: VNPay gateway
 *    - CASH: Tiền mặt
 *    - BANK_TRANSFER: Chuyển khoản
 * 6. paymentStatus (String): Trạng thái thanh toán
 *    - PAID: Thanh toán thành công
 *    - PENDING: Chờ xử lý
 *    - FAILED: Thanh toán thất bại
 *    - REFUNDED: Đã hoàn tiền
 * 7. createdAt (Timestamp): Thời điểm tạo hóa đơn
 * 
 * IMPORTANT NOTES:
 * - ❌ REMOVED eventId: Bill KHÔNG liên kết trực tiếp với 1 event
 * - Mỗi Bill có thể chứa nhiều Tickets từ nhiều Events khác nhau
 * - eventId được lưu ở Ticket table, không phải Bill table
 * - 1 Bill = 1 giao dịch thanh toán, có thể mua nhiều vé cùng lúc
 * 
 * CONSTRUCTORS:
 * 1. Bill(): Default constructor
 * 2. Bill(userId, totalAmount, currency, paymentMethod, paymentStatus):
 *    - Tạo bill mới khi user mua vé
 *    - createdAt tự động set = now
 * 
 * BUSINESS FLOW:
 * 1. User chọn vé muốn mua (có thể nhiều vé, nhiều event)
 * 2. Tính tổng tiền (totalAmount)
 * 3. Tạo Bill mới với status = PENDING
 * 4. Chuyển sang VNPay để thanh toán
 * 5. VNPay callback: Cập nhật paymentStatus = PAID
 * 6. Tạo Tickets và liên kết với billId
 * 7. Gửi email QR code vé cho user
 * 
 * EXAMPLE:
 * Bill bill = new Bill(userId, new BigDecimal("500000"), "VND", "VNPAY", "PENDING");
 * int billId = billDAO.insertBillAndReturnId(bill);
 * // ... VNPay payment ...
 * billDAO.updatePaymentStatus(billId, "PAID");
 * 
 * USE CASES:
 * - Tạo hóa đơn khi mua vé (BuyTicketController)
 * - Hiển thị lịch sử thanh toán (MyBillsController)
 * - Quản lý giao dịch thanh toán (BillDAO)
 * - Báo cáo doanh thu (RevenueReportController)
 * 
 * KẾT NỐI FILE:
 * - DAO: DAO/BillDAO.java
 * - Response: DTO/BillResponse.java (JOIN với Users.full_name)
 * - Controller: controller/MyBillsController.java
 * - Controller: controller/BuyTicketController.java
 * - Entity: DTO/Ticket.java (liên kết qua bill_id)
 */

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Bill {
    private int billId;
    private int userId;

    private BigDecimal totalAmount;
    private String currency;
    private String paymentMethod;
    private String paymentStatus;
    private Timestamp createdAt;

    // Constructors
    public Bill() {
    }

    public Bill(int userId, BigDecimal totalAmount, String currency,
            String paymentMethod, String paymentStatus) {
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters & Setters
    public int getBillId() {
        return billId;
    }

    public void setBillId(int billId) {
        this.billId = billId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    // ❌ REMOVED: getEventId() / setEventId()

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Bill{" +
                "billId=" + billId +
                ", userId=" + userId +
                ", totalAmount=" + totalAmount +
                ", currency='" + currency + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}