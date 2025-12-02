package config;

public class VnPayConfig {

    public static final String vnp_PayUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    public static final String vnp_TmnCode = "1UXH7CBM";
    public static final String vnp_HashSecret = "BBEK2UDHHRFDBSF8DBJWLV0JP5DEU0SX";

    // RETURN URL ĐÚNG CỦA DỰ ÁN
    public static final String vnp_ReturnUrl =
            "http://localhost:8084/FPTEventManagement/api/buyTicket";
}
