package nz.etu.voting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.request.AdminLoginRequest;
import nz.etu.voting.domain.dto.response.AdminLoginResponse;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/admin/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        log.debug("Received login request for user: {}", request.getUsername());
        try {
            AdminLoginResponse response = authService.adminLogin(request);
            log.debug("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

}