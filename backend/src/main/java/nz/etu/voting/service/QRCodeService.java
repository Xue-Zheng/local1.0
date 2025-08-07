package nz.etu.voting.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.EventMember;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class QRCodeService {

    public byte[] generateQRCodeForEventMember(EventMember eventMember) {
        try {
            String qrContent = buildQRContent(eventMember);
            return generateQRCodeImage(qrContent, 300, 300);
        } catch (Exception e) {
            log.error("Failed to generate QR code for member {}: {}",
                    eventMember.getMembershipNumber(), e.getMessage());
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public byte[] generateQRCodeImage(String content, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (bitMatrix.get(x, y)) {
                    graphics.fillRect(x, y, 1, 1);
                }
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private String buildQRContent(EventMember eventMember) {

        return String.format("{\"membershipNumber\":\"%s\",\"name\":\"%s\",\"eventId\":%d,\"token\":\"%s\",\"checkInUrl\":\"https://events.etu.nz/api/checkin/%s\"}",
                eventMember.getMembershipNumber(),
                eventMember.getName(),
                eventMember.getEvent().getId(),
                eventMember.getToken().toString(),
                eventMember.getToken().toString());
    }

    public String generateStaffScannerQRCode(Long eventId, String membershipNumber, String token) {
        // Generate QR code data that contains member information for staff scanning
        return String.format("{\"eventId\":%d,\"membershipNumber\":\"%s\",\"token\":\"%s\",\"type\":\"staff_checkin\"}",
                eventId, membershipNumber, token);
    }

    //    生成包含时间戳的QR码数据，用于多地区管理员扫码
    public String generateVenueCheckinQRCode(Long eventId, String venueName, String adminToken) {
        return String.format("{\"eventId\":%d,\"venue\":\"%s\",\"adminToken\":\"%s\",\"type\":\"venue_checkin\",\"timestamp\":%d}",
                eventId, venueName, adminToken, System.currentTimeMillis());
    }

    //    为会员生成包含更多信息的QR码
    public String generateMemberQRCode(EventMember eventMember) {
        return String.format("{\"membershipNumber\":\"%s\",\"name\":\"%s\",\"eventId\":%d,\"token\":\"%s\",\"type\":\"member_checkin\",\"timestamp\":%d}",
                eventMember.getMembershipNumber(),
                eventMember.getName(),
                eventMember.getEvent().getId(),
                eventMember.getToken().toString(),
                System.currentTimeMillis());
    }
}