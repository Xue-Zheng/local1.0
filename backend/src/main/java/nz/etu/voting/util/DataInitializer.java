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
        // Check if admin user exists
        Admin existingAdmin = adminRepository.findByUsername("admin").orElse(null);
        
        if (existingAdmin == null) {
            // Create new admin user if doesn't exist
            log.info("Creating default admin user");
            Admin admin = Admin.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .name("System Administrator")
                    .primaryEmail("admin@etu.nz")
                    .role("ADMIN")
                    .isActive(true)
                    .build();

            adminRepository.save(admin);
            log.info("Default admin user created with username: admin");
        } else {
            // Update existing admin password if it's the old weak password
            if (passwordEncoder.matches("admin123", existingAdmin.getPassword())) {
                log.info("Updating admin password to secure version");
                existingAdmin.setPassword(passwordEncoder.encode(defaultAdminPassword));
                adminRepository.save(existingAdmin);
                log.info("Admin password updated to secure version");
            }
        }
    }
}
