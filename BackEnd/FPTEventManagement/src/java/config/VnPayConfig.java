package config;

/**
 * ========================================================================================================
 * CONFIG: VnPayConfig - CẤU HÌNH VNPAY PAYMENT GATEWAY
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Lưu cấu hình kết nối VNPay Payment Gateway
 * - Chứa thông tin: Terminal ID, Secret Key, Payment URL, Return URL
 * - Dùng cho chức năng mua vé sự kiện online
 * 
 * VNPAY CONSTANTS:
 * 
 * 1. vnp_PayUrl: URL VNPay Payment Gateway
 * - Sandbox: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
 * - Production: https://www.vnpay.vn/paymentv2/vpcpay.html
 * - Hiện tại: Dùng sandbox để test
 * 
 * 2. vnp_TmnCode: Terminal/Merchant Code
 * - Mã đơn vị tiếp nhận được VNPay cấp
 * - Đăng ký tại: https://sandbox.vnpayment.vn/
 * - Giá trị hiện tại: "1UXH7CBM" (sandbox account)
 * 
 * 3. vnp_HashSecret: Secret Key để ký HMAC SHA512
 * - Dùng để mã hóa và xác thực chữ ký giao dịch
 * - Lấy từ VNPay portal sau khi đăng ký
 * - ⚠️ BẢO MẬT: Không public secret key lên GitHub
 * - Nên chuyển sang environment variable trong production
 * 
 * 4. vnp_ReturnUrl: Callback URL sau khi thanh toán
 * - URL Backend xử lý kết quả thanh toán từ VNPay
 * - Format: http://localhost:8084/FPTEventManagement/api/buyTicket
 * - VNPay sẽ redirect user về URL này sau khi thanh toán
 * - Backend xác thực chữ ký và cập nhật ticket status
 * 
 * VNPAY FLOW:
 * 1. User chọn vé và click "Thanh toán"
 * 2. PaymentTicketController tạo URL VNPay với các tham số:
 * - vnp_Amount: Số tiền (VND * 100)
 * - vnp_OrderInfo: Thông tin đơn hàng (userId, eventId, seatIds...)
 * - vnp_TxnRef: Mã giao dịch duy nhất
 * - vnp_SecureHash: Chữ ký HMAC SHA512
 * 3. Redirect user sang VNPay payment page
 * 4. User thanh toán qua VNPay (QR, ATM, Vi...)
 * 5. VNPay callback về vnp_ReturnUrl với kết quả
 * 6. BuyTicketController verify chữ ký VNPay
 * 7. Cập nhật ticket status = BOOKED, tạo QR code, gửi email
 * 
 * SECURITY:
 * - HMAC SHA512: Mã hóa chữ ký giao dịch
 * - Verify chữ ký VNPay trước khi cập nhật DB
 * - Secret key không được lộ ra ngoài
 * - Nên dùng HTTPS cho vnp_ReturnUrl trong production
 * 
 * PRODUCTION CHECKLIST:
 * - Đổi vnp_PayUrl sang production URL
 * - Đăng ký merchant thật với VNPay
 * - Lấy vnp_TmnCode và vnp_HashSecret mới
 * - Chuyển config sang environment variables
 * - Dùng HTTPS cho vnp_ReturnUrl
 * - Test thanh toán thật với số tiền nhỏ
 * 
 * KẾT NỐI FILE:
 * - Utils: config/VnPayUtil.java (tạo payment URL, HMAC SHA512)
 * - Controller: controller/PaymentTicketController.java (tạo URL thanh toán)
 * - Controller: controller/BuyTicketController.java (xử lý callback)
 * - Docs: https://sandbox.vnpayment.vn/apis/docs/
 */

public class VnPayConfig {

    public static final String vnp_PayUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String vnp_TmnCode = "1UXH7CBM";
    public static final String vnp_HashSecret = "BBEK2UDHHRFDBSF8DBJWLV0JP5DEU0SX";

    // RETURN URL ĐÚNG CỦA DỰ ÁN
    public static final String vnp_ReturnUrl = "http://localhost:8084/FPTEventManagement/api/buyTicket";
}