package nz.etu.voting.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ApiResponse;
import nz.etu.voting.domain.dto.response.ImportResponse;
import nz.etu.voting.service.ImportService;
import nz.etu.voting.service.InformerSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/admin/import")
@CrossOrigin(origins = {"http://localhost:3000","http://10.0.9.238:3000","https://events.etu.nz"})
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final InformerSyncService informerSyncService;

    @PostMapping("/members")
    public ResponseEntity<ApiResponse<ImportResponse>> importMembers(@RequestParam("file") MultipartFile file) {
        log.info("Received request to import members. File name: {}, Size: {}",
                file.getOriginalFilename(), file.getSize());
        try {
            ImportResponse response = importService.importMembersFromCsv(file);
            return ResponseEntity.ok(ApiResponse.success("Members imported successfully", response));
        } catch (Exception e) {
            log.error("Failed to import members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/members-with-details")
    public ResponseEntity<ApiResponse<ImportResponse>> importMembersWithDetails(@RequestParam("file") MultipartFile file) {
        log.info("Received request to import members with details from CSV. File name: {}, Size: {}",
                file.getOriginalFilename(), file.getSize());
        try {
            ImportResponse response = importService.importMembersFromCsvWithDetails(file);
            return ResponseEntity.ok(ApiResponse.success("Members imported successfully", response));
        } catch (Exception e) {
            log.error("Failed to import members: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/sync-now")
    public ResponseEntity<ApiResponse<String>> triggerSyncNow() {
        log.info("Received manual sync trigger request");
        try {
            informerSyncService.syncAllPendingEvents();
            return ResponseEntity.ok(ApiResponse.success("Manual sync triggered successfully", "Sync task has started"));
        } catch (Exception e) {
            log.error("Manual trigger failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Import test endpoint works!");
    }
}