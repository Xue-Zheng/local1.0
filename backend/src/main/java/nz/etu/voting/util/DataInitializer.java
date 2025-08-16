package nz.etu.voting.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Admin;
import nz.etu.voting.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default.password:ETU_Admin_2025_#Secure$}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
//        只有在没有管理员时才初始化
        if (adminRepository.count() == 0) {
            log.info("Initializing default admin user");

            Admin admin = Admin.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .name("System Administrator")
                    .primaryEmail("admin@etu.nz")
                    .role("ADMIN")
                    .isActive(true)
                    .build();

            adminRepository.save(admin);

            log.info("Default admin user created with username: admin (password configured via environment variable)");
        }
    }
}
