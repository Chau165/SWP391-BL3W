package mylib;

/**
 * ========================================================================================================
 * SERVICE: EmailService - GỬI EMAIL QUA SMTP (GMAIL)
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Gửi email OTP đăng ký tài khoản
 * - Gửi email tùy chỉnh (thông báo sự kiện, reset password...)
 * - Sinh mã OTP 6 chữ số ngẫu nhiên
 * - Sử dụng SMTP Gmail với App Password
 * 
 * SMTP CONFIGURATION:
 * - Host: smtp.gmail.com
 * - Port: 587 (STARTTLS)
 * - Protocol: TLSv1.2
 * - Auth: Username (email) + App Password
 * 
 * EMAIL CREDENTIALS:
 * - EMAIL_FROM: evbatteryswap.system@gmail.com (email gửi đi)
 * - EMAIL_PASSWORD: mzqbrzycduxhvbnr (App Password - KHÔNG phải mật khẩu Gmail thường)
 * - LƯU Ý: KHÔNG hard-code trong production, dùng environment variable
 * 
 * APP PASSWORD LÀ GÌ?
 * - App Password: Mật khẩu đặc biệt do Google sinh ra cho ứng dụng bên ngoài
 * - Không dùng mật khẩu Gmail thường (bảo mật hơn)
 * - Tạo ở: Google Account -> Security -> 2-Step Verification -> App passwords
 * - Format: 16 ký tự, không có khoảng trắng
 * 
 * METHODS:
 * 1. sendRegistrationOtpEmail(toEmail, otp):
 *    - Gửi email chứa mã OTP đăng ký
 *    - Template HTML đẹp, responsive
 *    - Subject: "Xác thực email đăng ký - FPT Event"
 *    - Return: true = success, false = failed
 * 
 * 2. sendCustomEmail(toEmail, subject, htmlContent):
 *    - Gửi email tùy chỉnh (cho các mục đích khác)
 *    - Subject và nội dung HTML do caller cung cấp
 *    - Return: true = success, false = failed
 * 
 * 3. createSession():
 *    - Tạo SMTP session với Gmail
 *    - Config STARTTLS, authentication
 *    - Private helper method
 * 
 * 4. generateOtp():
 *    - Sinh mã OTP 6 chữ số ngẫu nhiên (100000 - 999999)
 *    - Return: String "123456"
 * 
 * EMAIL TEMPLATE (OTP):
 * - HTML format với CSS inline
 * - Hiển thị OTP to, rõ ràng trong box màu xanh
 * - Thông báo OTP có hiệu lực 5 phút
 * - Chữ ký: FPT Event Management
 * - Responsive, hiển thị tốt trên mobile
 * 
 * LUỒNG GỬI EMAIL:
 * 1. Controller gọi EmailService.generateOtp()
 * 2. Controller gọi EmailService.sendRegistrationOtpEmail(email, otp)
 * 3. EmailService.createSession() tạo SMTP connection
 * 4. Tạo MimeMessage với HTML content
 * 5. Transport.send(message) gửi email qua Gmail SMTP
 * 6. Return true/false
 * 
 * ERROR HANDLING:
 * - Catch Exception: MessagingException, UnsupportedEncodingException
 * - Log error ra console để debug
 * - Return false nếu gửi thất bại
 * 
 * GMAIL SMTP LIMITS:
 * - Free Gmail: 500 emails/day
 * - Google Workspace: 2000 emails/day
 * - Rate limit: 100 emails/minute
 * - Nếu vượt: blocked tạm thời 24h
 * 
 * PRODUCTION BEST PRACTICES:
 * - Dùng email service chuyên nghiệp: SendGrid, AWS SES, Mailgun
 * - Lưu credentials trong environment variables
 * - Thêm email queue để không block request
 * - Monitor email delivery rate
 * - Handle bounce, spam complaints
 * 
 * SỬ DỤNG:
 * - Controller: RegisterSendOtpController, RegisterResendOtpController
 * - Cache: OtpCache (lưu OTP sau khi sinh)
 * - Config: SMTP settings (host, port, credentials)
 */

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * EmailService - Gửi email OTP đăng ký tài khoản
 * Dùng cho Java 8 + NetBeans + Ant
 */
public class EmailService {

    // ================== CẤU HÌNH SMTP ==================
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String EMAIL_FROM = "evbatteryswap.system@gmail.com"; // thay bằng mail của project mới nếu cần
    private static final String EMAIL_PASSWORD = "mzqbrzycduxhvbnr"; // App Password

    // ================== 1) GỬI EMAIL OTP ĐĂNG KÝ ==================
    public static boolean sendRegistrationOtpEmail(String toEmail, String otp) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Management", "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("Xác thực email đăng ký - FPT Event", "UTF-8");

            String html = "<html><body style='font-family:Arial,sans-serif;'>"
                    + "<h2 style='color:#2196F3;text-align:center;'>XÁC THỰC EMAIL ĐĂNG KÝ</h2>"
                    + "<p>Cảm ơn bạn đã đăng ký tài khoản "
                    + "<strong>FPT Event Management</strong>.</p>"
                    + "<p>Mã OTP xác thực của bạn là:</p>"
                    + "<div style='background:#e3f2fd;padding:15px;border-radius:6px;text-align:center;'>"
                    + "<h1 style='color:#2196F3;letter-spacing:4px;'>" + otp + "</h1>"
                    + "<p>Mã OTP có hiệu lực trong 5 phút.</p>"
                    + "</div>"
                    + "<p>Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.</p>"
                    + "<hr><p style='text-align:center;font-size:12px;color:#888;'>FPT Event Management</p>"
                    + "</body></html>";

            message.setContent(html, "text/html; charset=UTF-8");
            Transport.send(message);
            System.out.println("[EmailService] ✅ Registration OTP sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EmailService] ❌ Error sending registration email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== 2) GỬI EMAIL TÙY CHỈNH (NẾU CẦN DÙNG SAU NÀY)
    // ==================
    public static boolean sendCustomEmail(String toEmail, String subject, String htmlContent) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Management", "UTF-8"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[EmailService] ✅ Custom email sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EmailService] ❌ Failed to send custom email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== 3) HÀM TẠO PHIÊN SMTP CHUNG ==================
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.debug", "true");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });
    }

    // ================== 4) SINH OTP 6 CHỮ SỐ ==================
    public static String generateOtp() {
        int otp = 100000 + (int) (Math.random() * 900000);
        return String.valueOf(otp);
    }
}