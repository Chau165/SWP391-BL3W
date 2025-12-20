package utils;

/**
 * ========================================================================================================
 * UTILS: RecaptchaUtils - XÁC THỰC GOOGLE reCAPTCHA
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Xác thực reCAPTCHA token từ Frontend với Google API
 * - Chống bot, spam, automated attacks
 * - Bảo vệ các form quan trọng: đăng ký, login, reset password
 * 
 * GOOGLE reCAPTCHA:
 * - reCAPTCHA v3: Invisible, kiểm tra hành vi người dùng (score 0.0-1.0)
 * - reCAPTCHA v2: Checkbox "I'm not a robot" hoặc image challenge
 * - Frontend gọi grecaptcha.execute() -> nhận token
 * - Backend verify token với Google API
 * 
 * SECRET KEY:
 * - Hard-coded trong code (KHÔNG NÊN LÀM THẾ)
 * - Nên lưu trong environment variable hoặc config file
 * - Secret key khác với Site key (Site key public, Secret key private)
 * 
 * GOOGLE VERIFY API:
 * - URL: https://www.google.com/recaptcha/api/siteverify
 * - Method: POST
 * - Params: secret, response (token từ FE)
 * - Response: { success: true/false, challenge_ts, hostname, error-codes }
 * 
 * METHOD:
 * - verify(gRecaptchaResponse): Kiểm tra token hợp lệ
 *   + Input: Token từ FE (grecaptcha.execute() hoặc getResponse())
 *   + Output: true = valid, false = invalid/expired/bot
 * 
 * ERROR CODES:
 * - missing-input-secret: Thiếu secret key
 * - invalid-input-secret: Secret key không hợp lệ
 * - missing-input-response: Thiếu token từ FE
 * - invalid-input-response: Token không hợp lệ hoặc hết hạn
 * - bad-request: Request không hợp lệ
 * - timeout-or-duplicate: Token đã dùng hoặc hết hạn
 * 
 * TEST BYPASS:
 * - Nếu token = "TEST_BYPASS" -> return true (cho test local)
 * - PHẢI XÓA khi deploy production
 * 
 * LUỒNG SỬ DỤNG:
 * 1. FE load reCAPTCHA script và site key
 * 2. User submit form (register, login...)
 * 3. FE gọi grecaptcha.execute() -> nhận token
 * 4. FE gửi token trong request body: { recaptchaToken: "..." }
 * 5. Backend gọi RecaptchaUtils.verify(token)
 * 6. Nếu false: trả về error (block request)
 * 7. Nếu true: tiếp tục xử lý
 * 
 * SỬ DỤNG:
 * - Controller: RegisterSendOtpController (verify trước khi gửi OTP)
 * - Controller: LoginController (verify trước khi login)
 * - Controller: ForgotPasswordController
 * 
 * LƯU Ý:
 * - reCAPTCHA có rate limit: 1000 requests/second
 * - Token chỉ dùng được 1 lần, hết hạn sau 2 phút
 * - Nên cache kết quả verify trong vài giây để tránh spam Google API
 */

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

public class RecaptchaUtils {

    // HARD-CODE SECRET KEY (KHÔNG DÙNG ENV NỮA)
    private static final String SECRET = "6LdvPQIsAAAAAIvC1z3UPeLA7vVwQbi6Wyf2PZd8";

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    public static boolean verify(String gRecaptchaResponse) {
        if (gRecaptchaResponse == null || gRecaptchaResponse.trim().isEmpty()) {
            System.err.println("[reCAPTCHA] Missing token from client");
            return false;
        }

        if (SECRET == null || SECRET.trim().isEmpty()) {
            System.err.println("[reCAPTCHA] SECRET key missing (null or empty)");
            return false;
        }

        if ("TEST_BYPASS".equals(gRecaptchaResponse))
            return true;

        try {
            String params = "secret=" + URLEncoder.encode(SECRET, "UTF-8")
                    + "&response=" + URLEncoder.encode(gRecaptchaResponse, "UTF-8");

            URL url = new URL(VERIFY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("User-Agent", "Java-Recaptcha-Client");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes("UTF-8"));
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            try (InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
                RecaptchaResponse resp = new Gson().fromJson(reader, RecaptchaResponse.class);

                if (resp == null) {
                    System.err.println("[reCAPTCHA] Empty response from Google");
                    return false;
                }

                if (!resp.success && resp.errorCodes != null) {
                    System.err.println("[reCAPTCHA] Verify failed. Error codes: " + resp.errorCodes);
                }

                return resp.success;
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[reCAPTCHA] Exception while verifying token: " + e.getMessage());
            return false;
        }
    }

    private static class RecaptchaResponse {
        boolean success;

        @SerializedName("challenge_ts")
        String challengeTs;

        String hostname;

        @SerializedName("error-codes")
        List<String> errorCodes;
    }
}