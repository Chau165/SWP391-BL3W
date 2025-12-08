package utils;

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

    // Load reCAPTCHA secret from environment variable to avoid committing secrets in source code.
    // Set the environment variable `RECAPTCHA_SECRET` on your server or in your runtime environment.
    private static final String SECRET = System.getenv("RECAPTCHA_SECRET");
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    public static boolean verify(String gRecaptchaResponse) {
        if (gRecaptchaResponse == null || gRecaptchaResponse.trim().isEmpty()) {
            return false;
        }

        // Bypass for testing in Swagger / local development
        if ("TEST_BYPASS".equals(gRecaptchaResponse)) {
            return true;
        }

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
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

            try (InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
                RecaptchaResponse resp = new Gson().fromJson(reader, RecaptchaResponse.class);
                return resp != null && resp.success;
            }

        } catch (Exception e) {
            e.printStackTrace();
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
