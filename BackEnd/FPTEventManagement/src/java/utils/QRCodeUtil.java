package utils;

/**
 * ========================================================================================================
 * UTILS: QRCodeUtil - SINH QR CODE CHO VÉ SỰ KIỆN
 * ========================================================================================================
 * 
 * MỤC ĐÍCH:
 * - Sinh QR code cho vé sự kiện (Ticket)
 * - Dùng thư viện ZXing (Zebra Crossing)
 * - Trả về Base64 string hoặc PNG bytes
 * - Gửi QR code trong email cho user
 * 
 * THƯ VIỆN:
 * - ZXing (com.google.zxing): Open-source QR code library
 * - Maven: com.google.zxing:core, com.google.zxing:javase
 * - GitHub: https://github.com/zxing/zxing
 * 
 * METHODS:
 * 
 * 1. generateQRCodeBase64(text, width, height):
 *    MỤC ĐÍCH:
 *    - Sinh QR code từ chuỗi text bất kỳ
 *    - Trả về Base64 string (PNG format)
 *    - Dùng để embed trong HTML email
 *    
 *    THAM SỐ:
 *    - text: Nội dung cần encode (URL, ticketId, JSON...)
 *    - width: Chiều rộng QR code (pixels)
 *    - height: Chiều cao QR code (pixels)
 *    
 *    RETURN:
 *    - Base64 string của PNG image
 *    - Ví dụ: "iVBORw0KGgoAAAANSUhEUgAA..."
 *    - Dùng trong email: <img src="data:image/png;base64,{base64}" />
 * 
 * 2. generateQRCodePngBytes(text, width, height):
 *    MỤC ĐÍCH:
 *    - Sinh QR code trả về byte array (PNG)
 *    - Dùng để lưu file hoặc upload Cloudinary
 *    
 *    RETURN:
 *    - byte[] của PNG image
 *    - Có thể write ra FileOutputStream
 *    - Hoặc upload lên cloud storage
 * 
 * 3. generateTicketQrBase64(ticketId, width, height):
 *    MỤC ĐÍCH:
 *    - Sinh QR code cho vé (wrapper method)
 *    - QR chỉ chứa ticketId (số nguyên)
 *    - Dùng cho check-in/check-out sự kiện
 *    
 *    LOGIC:
 *    - Text = String.valueOf(ticketId)
 *    - Ví dụ: ticketId = 123 -> QR chứa "123"
 *    - Scan QR -> đọc được ticketId -> query DB
 *    
 *    USE CASE:
 *    - User mua vé -> nhận email với QR code
 *    - Tại cổng check-in -> quét QR -> lấy ticketId
 *    - Backend query Ticket table -> kiểm tra hợp lệ
 *    - Cập nhật status = CHECKED_IN
 * 
 * 4. generateTicketQrPngBytes(ticketId, width, height):
 *    - Tương tự generateTicketQrBase64 nhưng trả về bytes
 *    - Dùng khi cần lưu QR code ra file
 * 
 * KÍCH THƯỚC QR CODE:
 * - Standard: 300x300 pixels (cho email, mobile scan)
 * - Large: 500x500 pixels (cho in ấn, poster)
 * - Small: 150x150 pixels (cho thumbnail)
 * 
 * ENCODING OPTIONS:
 * - ZXing default: UTF-8
 * - Error correction: Medium (Level M, 15% recovery)
 * - Format: PNG (lấy từ MatrixToImageWriter)
 * 
 * EMAIL FLOW:
 * 1. User mua vé thành công
 * 2. BuyTicketController gọi QRCodeUtil.generateTicketQrBase64(ticketId, 300, 300)
 * 3. Nhận Base64 string
 * 4. Tạo HTML email với <img src="data:image/png;base64,{qrBase64}" />
 * 5. EmailUtils.sendEmailWithImage() gửi email
 * 6. User mở email thấy QR code
 * 
 * CHECK-IN FLOW:
 * 1. User đến cổng sự kiện
 * 2. Staff quét QR code bằng mobile app
 * 3. App đọc được ticketId (ví dụ: "123")
 * 4. Gọi API: POST /api/checkin { ticketId: 123 }
 * 5. Backend kiểm tra Ticket.status
 * 6. Cập nhật status = CHECKED_IN
 * 7. Return success -> mở cổng cho user vào
 * 
 * SECURITY:
 * - QR chỉ chứa ticketId (public info)
 * - Backend kiểm tra ticket hợp lệ qua DB
 * - Không embed sensitive data trong QR (password, token...)
 * - Nên thêm timestamp để chống replay attack trong production
 * 
 * NÂNG CẤP ĐỀ XUẤT:
 * - QR chứa JWT token thay vì plain ticketId (chống fake)
 * - QR chứa timestamp + signature (chống replay)
 * - Dynamic QR (refresh mỗi 30s, chống screenshot)
 * - Thêm logo FPT vào giữa QR code (branding)
 * 
 * KẾT NỐI FILE:
 * - Controller: controller/BuyTicketController.java (tạo QR sau khi mua vé)
 * - Utils: utils/EmailUtils.java (gửi QR qua email)
 * - DAO: DAO/TicketDAO.java (lưu qr_code_value và qr_issued_at)
 * - Controller: controller/CheckinController.java (quét QR)
 */

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * QRCodeUtil - dùng chung cho hệ thống Event
 * - Tạo QR code từ chuỗi text
 * - Hoặc từ ticketId (QR chỉ chứa ticketId)
 */
public class QRCodeUtil {

    /**
     * Generate QR Code as Base64 string (PNG)
     * 
     * @param text   Nội dung muốn encode vào QR code
     * @param width  Chiều rộng
     * @param height Chiều cao
     * @return Base64 encoded image string (PNG)
     * @throws Exception
     */
    public static String generateQRCodeBase64(String text, int width, int height) throws Exception {
        byte[] pngData = generateQRCodePngBytes(text, width, height);
        return Base64.getEncoder().encodeToString(pngData);
    }

    /**
     * Generate QR Code PNG bytes (tiện để upload lên Cloudinary / lưu file)
     * 
     * @param text
     * @param width
     * @param height
     * @return
     * @throws java.lang.Exception
     */
    public static byte[] generateQRCodePngBytes(String text, int width, int height) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix;
        try {
            bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
        } catch (WriterException e) {
            throw new Exception("Error generating QR code: " + e.getMessage(), e);
        }

        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Generate QR cho TICKET: QR chỉ chứa ticket_id (dạng chuỗi)
     * => Khi scan, backend chỉ cần đọc ticket_id rồi truy DB để lấy full info.
     * 
     * @param ticketId
     * @param width
     * @param height
     * @return
     * @throws java.lang.Exception
     */
    public static String generateTicketQrBase64(int ticketId, int width, int height) throws Exception {
        String text = String.valueOf(ticketId); // ✅ QR chỉ chứa ticket_id
        return generateQRCodeBase64(text, width, height);
    }

    /**
     * Nếu bạn muốn lấy dạng bytes cho Cloudinary:
     * 
     * @param ticketId
     * @param width
     * @param height
     * @return
     * @throws java.lang.Exception
     */
    public static byte[] generateTicketQrPngBytes(int ticketId, int width, int height) throws Exception {
        String text = String.valueOf(ticketId);
        return generateQRCodePngBytes(text, width, height);
    }
}