package nz.etu.voting.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.AdminLoginRequest;
import nz.etu.voting.domain.dto.response.AdminLoginResponse;
import nz.etu.voting.domain.entity.Admin;
import nz.etu.voting.exception.ResourceNotFoundException;
import nz.etu.voting.repository.AdminRepository;
import nz.etu.voting.service.AuthService;
import nz.etu.voting.util.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdminRepository adminRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Override
    public AdminLoginResponse adminLogin(AdminLoginRequest request) {
        log.debug("Attempting login with username: {}", request.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            log.debug("Authentication successful for user: {}", request.getUsername());

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtUtil.generateToken(userDetails);

            Admin admin = adminRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));

            admin.setLastLogin(LocalDateTime.now());
            adminRepository.save(admin);

            return AdminLoginResponse.builder()
                    .id(admin.getId())
                    .username(admin.getUsername())
                    .name(admin.getName())
                    .primaryEmail(admin.getPrimaryEmail())
                    .role(admin.getRole())
                    .token(token)
                    .build();
        } catch (BadCredentialsException e) {
            log.error("Invalid credentials for user: {}", request.getUsername());
            throw new RuntimeException("Invalid username or password", e);
        } catch (Exception e) {
            log.error("Authentication failed for user: {}", request.getUsername(), e);
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }
}