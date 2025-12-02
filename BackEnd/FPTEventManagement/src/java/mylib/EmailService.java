package mylib;

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
 * EmailService - Gửi email xác thực và link đặt lại mật khẩu theo JWT
 * Dành cho Java 8 + NetBeans + Ant
 */
public class EmailService {

    // ================== CẤU HÌNH SMTP ==================
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String EMAIL_FROM = "evbatteryswap.system@gmail.com";
    private static final String EMAIL_PASSWORD = "mzqbrzycduxhvbnr"; // Gmail App Password

    // ================== 1) GỬI EMAIL XÁC THỰC ĐĂNG KÝ (OTP) ==================
    public static boolean sendRegistrationOtpEmail(String toEmail, String otp) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Ticketing System", "UTF-8"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject("Xác thực Email Đăng Ký - FPT Event Ticketing System", "UTF-8");

                String html = "<html><body style='font-family:Arial,sans-serif;'>"
                    + "<h2 style='color:#2196F3;text-align:center;'>✉️ Xác Thực Email Đăng Ký</h2>"
                    + "<p>Cảm ơn bạn đã đăng ký tài khoản <strong>FPT Event Ticketing System</strong>.</p>"
                    + "<p>Mã OTP xác thực của bạn là:</p>"
                    + "<div style='background:#e3f2fd;padding:15px;border-radius:6px;text-align:center;'>"
                    + "<h1 style='color:#2196F3;'>" + otp + "</h1>"
                    + "<p>Mã OTP có hiệu lực trong 5 phút.</p>"
                    + "</div>"
                    + "<p>Nếu bạn không yêu cầu đăng ký, vui lòng bỏ qua email này.</p>"
                    + "<hr><p style='text-align:center;font-size:12px;color:#888;'>FPT Event Ticketing System</p>"
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

    // ================== 2) GỬI LINK ĐẶT LẠI MẬT KHẨU (JWT) ==================
    public static boolean sendCustomEmail(String toEmail, String subject, String htmlContent) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Ticketing System", "UTF-8"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[EmailService] ✅ Email sent (FPT Event Ticketing System) to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EmailService] ❌ Failed to send email (FPT Event Ticketing System): " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== 3) HÀM TẠO PHIÊN SMTP CHUNG ==================
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true"); // STARTTLS
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        // Java 8 thường cần ép TLSv1.2
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.debug", "true");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });
    }

    // ================== 4) HỖ TRỢ SINH OTP ==================
    public static String generateOtp() {
        int otp = 100000 + (int) (Math.random() * 900000);
        return String.valueOf(otp);
    }
}
