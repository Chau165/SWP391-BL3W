package DTO;

/**
 * ========================================================================================================
 * DTO: BillResponse - RESPONSE HÓA ĐƠN CHO API MY-BILLS
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Response DTO cho API GET /api/payment/my-bills
 * - Chứa thông tin hóa đơn được JOIN với thông tin user (full_name)
 * - Hiển thị danh sách hóa đơn của sinh viên trên FE
 * 
 * FIELDS:
 * 1. billId (int): ID hóa đơn (primary key Bill table)
 * 2. totalAmount (BigDecimal): Tổng số tiền thanh toán
 *    - Đơn vị: VND (hoặc theo currency field)
 *    - Ví dụ: 500000 VND (500k)
 * 3. currency (String): Đơn vị tiền tệ (VND, USD...)
 * 4. paymentMethod (String): Phương thức thanh toán
 *    - VNPAY: Thanh toán VNPay
 *    - CASH: Tiền mặt (nếu có)
 *    - BANK_TRANSFER: Chuyển khoản ngân hàng
 * 5. paymentStatus (String): Trạng thái thanh toán
 *    - PAID: Đã thanh toán thành công
 *    - PENDING: Chờ thanh toán
 *    - FAILED: Thanh toán thất bại
 *    - REFUNDED: Đã hoàn tiền
 * 6. createdAt (Timestamp): Thời điểm tạo hóa đơn
 * 7. userName (String): Tên người mua (full_name từ Users table)
 * 
 * KHÁC BIỆT VỚI Bill.java:
 * - Bill.java: Entity ánh xạ truy cập bảng Bill trong DB
 * - BillResponse.java: Response DTO có thêm userName từ JOIN Users
 * - Bill có userId (FK), BillResponse có userName (display)
 * 
 * SQL MAPPING:
 * SELECT b.bill_id, b.total_amount, b.currency, b.payment_method,
 *        b.payment_status, b.created_at, u.full_name AS userName
 * FROM Bill b
 * JOIN Users u ON b.user_id = u.user_id
 * WHERE b.user_id = ?
 * ORDER BY b.created_at DESC
 * 
 * EXAMPLE JSON:
 * {
 *   "billId": 456,
 *   "totalAmount": 500000,
 *   "currency": "VND",
 *   "paymentMethod": "VNPAY",
 *   "paymentStatus": "PAID",
 *   "createdAt": "2025-01-10T10:30:00",
 *   "userName": "Nguyễn Văn A"
 * }
 * 
 * USE CASES:
 * - Trang "Lịch sử thanh toán" của sinh viên
 * - Hiển thị danh sách các hóa đơn đã mua vé
 * - Lọc theo paymentStatus (PAID, PENDING...)
 * - Tìm kiếm theo ngày (createdAt)
 * 
 * KẾT NỐI FILE:
 * - Controller: controller/MyBillsController.java
 * - DAO: DAO/BillDAO.java (getBillsByUserId method)
 * - Entity: DTO/Bill.java
 */

import java.math.BigDecimal;
import java.sql.Timestamp;

public class BillResponse {

    private int billId;
    private BigDecimal totalAmount;
    private String currency;
    private String paymentMethod;
    private String paymentStatus;
    private Timestamp createdAt;
    private String userName;

    public int getBillId() {
        return billId;
    }

    public void setBillId(int billId) {
        this.billId = billId;
    }

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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}