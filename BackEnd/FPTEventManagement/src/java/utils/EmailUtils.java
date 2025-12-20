package utils;

/**
 * ========================================================================================================
 * UTILS: EmailUtils - GỬI EMAIL VỚI HÌNH ẢNH INLINE
 * ========================================================================================================
 * 
 * CHỨC NĂNG:
 * - Gửi email với hình ảnh inline (embedded image trong HTML)
 * - Gửi email OTP đăng ký (giữ lại tương thích với EmailService)
 * - Gửi email tùy chỉnh (thông báo, reset password...)
 * - Sinh mã OTP 6 chữ số
 * - Sử dụng SMTP Gmail với App Password
 * 
 * SO SÁNH VỚI EmailService:
 * - EmailService.java: Gửi email đơn giản (HTML only)
 * - EmailUtils.java: Gửi email với hình ảnh inline (MimeMultipart)
 * - Chức năng: EmailUtils mạnh hơn, hỗ trợ QR code, logo, banner trong email
 * - Nên merge 2 class này thành 1 trong tương lai
 * 
 * SMTP CONFIGURATION:
 * - Host: smtp.gmail.com
 * - Port: 587 (STARTTLS)
 * - Protocol: TLSv1.2
 * - Auth: Username (email) + App Password
 * 
 * EMAIL CREDENTIALS:
 * - EMAIL_FROM: evbatteryswap.system@gmail.com
 * - EMAIL_PASSWORD: mzqbrzycduxhvbnr (App Password)
 * - LƯU Ý: Giống EmailService.java, nên tạo config chung
 * 
 * METHODS:
 * 
 * 1. sendEmailWithImage(to, subject, htmlBody, imageBytes, imageContentId):
 *    MỤC ĐÍCH:
 *    - Gửi email HTML với hình ảnh inline (embedded)
 *    - Hình ảnh hiển thị ngay trong email, không cần download
 *    - Dùng cho QR code vé, logo company, banner sự kiện
 *    
 *    THAM SỐ:
 *    - to: Email người nhận
 *    - subject: Tiêu đề email
 *    - htmlBody: Nội dung HTML (chứa <img src="cid:imageContentId">)
 *    - imageBytes: Byte array của hình ảnh (PNG, JPG...)
 *    - imageContentId: ID để reference trong HTML (ví dụ: "qrcode")
 *    
 *    VÍ DỤ:
 *    String html = "<img src='cid:qrcode' />";
 *    byte[] qrBytes = QRCodeGenerator.generate(data);
 *    EmailUtils.sendEmailWithImage(email, "Your QR Code", html, qrBytes, "qrcode");
 *    
 *    KỸ THUẬT:
 *    - MimeMultipart "related": Liên kết HTML và image
 *    - Part 1: HTML content
 *    - Part 2: Image với Content-ID header
 *    - HTML reference image qua <img src="cid:xxx">
 * 
 * 2. sendRegistrationOtpEmail(toEmail, otp):
 *    - Duplicate của EmailService.sendRegistrationOtpEmail()
 *    - Giữ lại để tương thích với code hiện tại
 *    - Nên xóa và dùng EmailService thay thế
 * 
 * 3. sendCustomEmail(toEmail, subject, htmlContent):
 *    - Gửi email HTML tùy chỉnh (không có hình inline)
 *    - Dùng cho thông báo, reset password, xác nhận đăng ký...
 *    - Giống EmailService.sendCustomEmail()
 * 
 * 4. generateOtp():
 *    - Sinh mã OTP 6 chữ số ngẫu nhiên (100000 - 999999)
 *    - Duplicate của EmailService.generateOtp()
 *    - Nên tạo Util chung cho OTP generation
 * 
 * 5. createSession():
 *    - Tạo SMTP session với Gmail
 *    - Config authentication và STARTTLS
 *    - Private helper method
 * 
 * MIME MULTIPART:
 * - MimeMultipart "related": Chứa HTML + inline resources
 * - MimeBodyPart: Mỗi part là 1 phần của email (HTML, image, attachment)
 * - Content-ID: Định danh hình ảnh trong HTML (<img src="cid:xxx">)
 * - DataHandler + ByteArrayDataSource: Convert byte[] thành MIME content
 * 
 * UỢC ĐIỂM:
 * - Hình ảnh hiển thị ngay, không bị blocked bởi email client
 * - Không phụ thuộc external URL (image hosting)
 * - Tốt cho QR code, logo, signature
 * 
 * NHƯỢC ĐIỂM:
 * - Tăng kích thước email (image embedded trong email)
 * - Không cache được (mỗi email chứa 1 copy image)
 * - Một số email client cũ có thể không hiển thị
 * 
 * USE CASES:
 * - Gửi QR code vé sự kiện trong email
 * - Gửi logo company trong email signature
 * - Gửi banner sự kiện trong email thông báo
 * - Gửi chart/graph trong báo cáo
 * 
 * REFACTOR ĐỀ XUẤT:
 * - Merge EmailService.java và EmailUtils.java thành 1 class
 * - Tạo EmailConfig.java cho SMTP credentials
 * - Tạo OtpUtils.java cho generateOtp()
 * - Tạo EmailTemplateService.java cho HTML templates
 * 
 * SỬ DỤNG:
 * - Controller: TicketController (gửi QR code vé)
 * - Service: NotificationService (gửi thông báo với logo)
 * - Controller: RegisterSendOtpController (OTP đăng ký)
 */

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

/**
 * EmailUtils - helper for sending emails with inline images
 */
public class EmailUtils {

    // Reuse same SMTP config as existing EmailService (adjust if needed)
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String EMAIL_FROM = "evbatteryswap.system@gmail.com"; // project email
    private static final String EMAIL_PASSWORD = "mzqbrzycduxhvbnr"; // App Password - keep in sync with EmailService

    public static void sendEmailWithImage(String to, String subject, String htmlBody, byte[] imageBytes,
            String imageContentId) throws Exception {
        Session session = createSession();

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Management", "UTF-8"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject, "UTF-8");

        MimeMultipart multipart = new MimeMultipart("related");

        // Part 1: HTML
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlPart);

        // Part 2: Image
        if (imageBytes != null && imageBytes.length > 0) {
            MimeBodyPart imagePart = new MimeBodyPart();
            ByteArrayDataSource bds = new ByteArrayDataSource(imageBytes, "image/png");
            imagePart.setDataHandler(new DataHandler(bds));
            imagePart.setHeader("Content-ID", "<" + imageContentId + ">");
            imagePart.setFileName(imageContentId + ".png");
            imagePart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(imagePart);
        }

        message.setContent(multipart);

        Transport.send(message);
        System.out.println("[EmailUtils] ✅ Email sent to: " + to + " (subject='" + subject + "')");
    }

    // ================== 1) GỬI EMAIL OTP ĐĂNG KÝ (giữ lại để tương thích với code
    // hiện có)
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
            System.out.println("[EmailUtils] ✅ Registration OTP sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EmailUtils] ❌ Error sending registration email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== 2) GỬI EMAIL TÙY CHỈNH (nếu cần dùng sau này)
    public static boolean sendCustomEmail(String toEmail, String subject, String htmlContent) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);

            message.setFrom(new InternetAddress(EMAIL_FROM, "FPT Event Management", "UTF-8"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[EmailUtils] ✅ Custom email sent to: " + toEmail);
            return true;

        } catch (Exception e) {
            System.err.println("[EmailUtils] ❌ Failed to send custom email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ================== 3) SINH OTP 6 CHỮ SỐ ==================
    public static String generateOtp() {
        int otp = 100000 + (int) (Math.random() * 900000);
        return String.valueOf(otp);
    }

    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.debug", "false");

        return Session.getInstance(props, new javax.mail.Authenticator() {
            @Override
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });
    }
}