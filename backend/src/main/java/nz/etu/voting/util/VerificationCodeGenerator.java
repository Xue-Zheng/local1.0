package nz.etu.voting.util;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class VerificationCodeGenerator {
    private static final String DIGITS = "0123456789";
    private final SecureRandom random = new SecureRandom();

    public String generateSixDigitCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        }
        return code.toString();
    }
}