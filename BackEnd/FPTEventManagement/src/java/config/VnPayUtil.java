// config/VnPayUtil.java
package config;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class VnPayUtil {

    // ===== 1. Hàm tạo HMAC SHA512 (giữ nguyên) =====
    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            System.out.println("❌ Error hmacSHA512: " + e.getMessage());
            return "";
        }
    }

    // ===== 2. Lấy IP client =====
    public static String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            int comma = ip.indexOf(',');
            return (comma > -1) ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        return (ip != null && !ip.isEmpty()) ? ip : req.getRemoteAddr();
    }

    // ===== 3. Tạo URL thanh toán VNPay dùng cho ĐẶT VÉ =====
    public static String createPaymentUrl(HttpServletRequest req,
                                          String txnRef,
                                          long amountVnd,
                                          String orderInfo,
                                          String orderType) throws Exception {

        // Lấy config
        String vnp_TmnCode    = VnPayConfig.vnp_TmnCode;
        String vnp_HashSecret = VnPayConfig.vnp_HashSecret;
        String vnp_Url        = VnPayConfig.vnp_PayUrl;
        String vnp_ReturnUrl  = VnPayConfig.vnp_ReturnUrl;

        System.out.println("===== [VnPayUtil] createPaymentUrl START =====");
        System.out.println("Config vnp_TmnCode = " + vnp_TmnCode);
        // KHÔNG in full secret trong log thực tế, chỉ in 1 phần để check
        System.out.println("Config vnp_HashSecret (prefix 6) = "
                + (vnp_HashSecret != null && vnp_HashSecret.length() > 6
                ? vnp_HashSecret.substring(0, 6) + "..." : vnp_HashSecret));
        System.out.println("Config vnp_PayUrl = " + vnp_Url);
        System.out.println("Config vnp_ReturnUrl = [" + vnp_ReturnUrl + "]");

        String vnp_Amount = String.valueOf(amountVnd * 100L);
        System.out.println("Amount input VND = " + amountVnd + " -> vnp_Amount=" + vnp_Amount);

        // Thời gian
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        df.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        String createDate = df.format(new Date());

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
        cal.add(Calendar.MINUTE, 15);
        String expireDate = df.format(cal.getTime());

        String clientIp = getClientIp(req);
        System.out.println("Client IP (for vnp_IpAddr) = " + clientIp);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", vnp_Amount);
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnp_ReturnUrl);
        vnp_Params.put("vnp_CreateDate", createDate);
        vnp_Params.put("vnp_ExpireDate", expireDate);
        vnp_Params.put("vnp_IpAddr", clientIp);
        // KHÔNG thêm vnp_SecureHash / vnp_SecureHashType ở đây để không bị tính vào hash

        System.out.println("----- VNPay Params (before sort) -----");
        for (Map.Entry<String, String> e : vnp_Params.entrySet()) {
            System.out.println(e.getKey() + " = " + e.getValue());
        }

        // Sort key
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        System.out.println("----- VNPay Params (after sort & encode) -----");
        for (Iterator<String> itr = fieldNames.iterator(); itr.hasNext();) {
            String key = itr.next();
            String value = vnp_Params.get(key);
            if (value != null && !value.isEmpty()) {
                String encValue = URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());

                if (hashData.length() > 0) {
                    hashData.append('&');
                }
                hashData.append(key).append('=').append(encValue);

                if (query.length() > 0) {
                    query.append('&');
                }
                // key thường không encode trong query cũng ok
                query.append(key).append('=').append(encValue);

                System.out.println(" " + key + " -> raw=[" + value + "], enc=[" + encValue + "]");
            }
        }

        System.out.println("----- HashData string (client side) -----");
        System.out.println(hashData.toString());

        String vnp_SecureHash = hmacSHA512(vnp_HashSecret, hashData.toString());

        System.out.println("vnp_SecureHash (client) = " + vnp_SecureHash);

        // Nếu muốn gửi cả vnp_SecureHashType:
        // query.append("&vnp_SecureHashType=HMACSHA512");
        query.append("&vnp_SecureHash=").append(vnp_SecureHash);

        String fullUrl = vnp_Url + "?" + query;

        System.out.println("----- Final VNPay URL -----");
        System.out.println(fullUrl);
        System.out.println("===== [VnPayUtil] createPaymentUrl END =====");

        return fullUrl;
    }
}
