package utils;

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
     * @param text Nội dung muốn encode vào QR code
     * @param width Chiều rộng
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
     * @param ticketId
     * @param width
     * @param height
     * @return 
     * @throws java.lang.Exception
     */
    public static String generateTicketQrBase64(int ticketId, int width, int height) throws Exception {
        String text = String.valueOf(ticketId);  // ✅ QR chỉ chứa ticket_id
        return generateQRCodeBase64(text, width, height);
    }

    /**
     * Nếu bạn muốn lấy dạng bytes cho Cloudinary:
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
