package nz.etu.voting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.EventRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.util.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InformerSyncService {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final MemberRepository memberRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final RestTemplate restTemplate;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${informer.base.url:https://etu-inf5-rsau.aptsolutions.net/api/datasets/}")
    private String informerBaseUrl;

    @Value("${informer.corruption.check.enabled:true}")
    private boolean corruptionCheckEnabled;

    // Three data source synchronization methods
    // Sync event attendee data (data source 1) - full version, creates Events, Members and EventMember tables
    // CRITICAL: Temporarily comment out attendee sync, focus on BMM events
    // @Transactional
    public void syncAttendeeDataFromUrl_DISABLED(String url) {
        log.info("Starting complete attendee data sync (Events + Members + EventMember) from: {}", url);
        String batchId = UUID.randomUUID().toString();

        try {
            String jsonData = fetchDataFromUrl(url);
            log.info("Fetched JSON data length: {}", jsonData != null ? jsonData.length() : 0);

            if (jsonData == null || jsonData.trim().isEmpty()) {
                log.error("No data received from URL: {}", url);
                throw new RuntimeException("No data received from URL");
            }

            JsonNode rootNode = objectMapper.readTree(jsonData);
            log.info("Parsed JSON root node type: {}", rootNode.getNodeType());

            JsonNode dataArray;
            if (rootNode.isArray()) {
                dataArray = rootNode;
                log.info("Data is direct array with {} elements", dataArray.size());
            } else if (rootNode.has("data")) {
                dataArray = rootNode.get("data");
                log.info("Data is nested under 'data' key with {} elements", dataArray.size());
            } else {
                log.error("Unexpected JSON structure. Root keys: {}", rootNode.fieldNames());
                throw new RuntimeException("Unexpected JSON structure");
            }

            if (dataArray == null || !dataArray.isArray()) {
                log.error("Data array is null or not an array");
                throw new RuntimeException("Invalid data array structure");
            }

//            Statistics counter
            Map<String, Integer> processedCounts = new HashMap<>();
            processedCounts.put("events", 0);
            processedCounts.put("members", 0);
            processedCounts.put("eventMembers", 0);

            log.info("Processing {} attendee records for complete import", dataArray.size());

            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode attendeeNode = dataArray.get(i);
                try {
//                    Process complete record: Event + Member + EventMember
//                    Use ApplicationContext to get proxy for REQUIRES_NEW transaction propagation
                    applicationContext.getBean(InformerSyncService.class).processCompleteAttendeeRecord(attendeeNode, batchId, i + 1, processedCounts);

                    if (i % 100 == 0) { // Log progress every 100 records
                        log.info("Processed {} of {} records - Events: {}, Members: {}, EventMembers: {}",
                                i + 1, dataArray.size(),
                                processedCounts.get("events"),
                                processedCounts.get("members"),
                                processedCounts.get("eventMembers"));
                    }
                } catch (Exception e) {
                    log.error("Failed to process complete attendee record {}: {}", i + 1, e.getMessage());
                }
            }

            log.info("Complete attendee data sync finished with batch ID: {} - Final counts: Events: {}, Members: {}, EventMembers: {}",
                    batchId,
                    processedCounts.get("events"),
                    processedCounts.get("members"),
                    processedCounts.get("eventMembers"));

        } catch (Exception e) {
            log.error("Complete attendee data sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Complete attendee data sync failed", e);
        }
    }

    // Sync active members with email (data source 2) - fixed version: batch processing to avoid Session timeout
    public void syncEmailMembersData(String url) {
        log.info("CRITICAL: Redirecting to DIRECT EventMember sync (bypassing Member table)");
        // CRITICAL: Fix - now directly call direct sync method to ensure complete JSON data is saved to EventMember table
        syncEmailMembersDirectlyToEventMember(url);
    }

    // Process single batch of email member data in independent transaction
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public int processEmailMembersBatch(JsonNode dataArray, int startIndex, int endIndex, String batchId) {
        log.warn("DEPRECATED METHOD CALLED: processEmailMembersBatch - Redirecting to direct EventMember sync");

        try {
            Event bmmEvent = getBMMEvent();
            if (bmmEvent == null) {
                bmmEvent = createDefaultBMMEvent();
            }

            // Use the new direct method instead of the old Member-based approach
            List<EventMember> eventMembersToSave = new ArrayList<>();

            for (int i = startIndex; i < endIndex; i++) {
                JsonNode memberNode = dataArray.get(i);
                try {
                    EventMember eventMember = createEventMemberFromInformerData(memberNode, "INFORMER_EMAIL_LEGACY_REDIRECT", batchId, bmmEvent);
                    if (eventMember != null) {
                        eventMembersToSave.add(eventMember);
                    }
                } catch (Exception e) {
                    log.error("Failed to process email member record {} in legacy redirect: {}", i + 1, e.getMessage());
                }
            }

            if (!eventMembersToSave.isEmpty()) {
                eventMemberRepository.saveAll(eventMembersToSave);
                log.info("Legacy redirect: Saved {} EventMembers directly (bypassed Member table)", eventMembersToSave.size());
                return eventMembersToSave.size();
            }

            return 0;
        } catch (Exception e) {
            log.error("Legacy redirect failed: {}", e.getMessage(), e);
            return 0;
        }
    }

    // CRITICAL: Direct sync email members to EventMember table
    public void syncEmailMembersDirectlyToEventMember(String url) {
        log.info("Starting DIRECT email members sync to EventMember from: {}", url);
        String batchId = UUID.randomUUID().toString();

        try {
            // CRITICAL: Fix connection leak - fetch external data first to avoid long waits during database connection
            log.info("Step 1: Fetching email data from external API...");
            String jsonData = fetchDataFromUrlWithTimeout(url, 600000); // 10 minutes timeout for 45000+ records
            if (jsonData == null || jsonData.trim().isEmpty()) {
                throw new RuntimeException("No data received from URL");
            }

            // CRITICAL: Additional validation for data integrity
            if (jsonData.length() < 100) {
                log.error("Received unexpectedly small response ({} chars), data may be corrupted", jsonData.length());
                throw new RuntimeException("Response too small, possible data corruption");
            }

            // CRITICAL: Debug and validate JSON structure before parsing
            log.info("Attempting to parse cleaned JSON data. First 200 characters: {}",
                    jsonData.length() > 200 ? jsonData.substring(0, 200) + "..." : jsonData);

            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(jsonData);
            } catch (Exception e) {
                log.error("Failed to parse JSON. Error: {}. First 500 chars: {}",
                        e.getMessage(),
                        jsonData.length() > 500 ? jsonData.substring(0, 500) + "..." : jsonData);
                throw new RuntimeException("JSON parsing failed: " + e.getMessage(), e);
            }

            JsonNode dataArray;
            if (rootNode.isArray()) {
                log.info("Root node is an array with {} elements", rootNode.size());
                dataArray = rootNode;
            } else if (rootNode.isObject()) {
                log.info("Root node is an object. Looking for 'data' field...");
                dataArray = rootNode.get("data");
                if (dataArray == null) {
                    // Try other common field names
                    dataArray = rootNode.get("results");
                    if (dataArray == null) {
                        dataArray = rootNode.get("records");
                    }
                    if (dataArray == null) {
                        dataArray = rootNode.get("items");
                    }
                }
                if (dataArray != null) {
                    log.info("Found data array in '{}' field with {} elements",
                            dataArray == rootNode.get("data") ? "data" :
                                    dataArray == rootNode.get("results") ? "results" :
                                            dataArray == rootNode.get("records") ? "records" : "items",
                            dataArray.size());
                }
            } else {
                log.error("Root node is neither array nor object. Node type: {}", rootNode.getNodeType());
                throw new RuntimeException("Invalid JSON root structure - not array or object");
            }

            if (dataArray == null || !dataArray.isArray()) {
                log.error("No valid data array found. Root object available field names: {}",
                        rootNode.isObject() ? rootNode.fieldNames() : "Not an object");
                throw new RuntimeException("Invalid data array structure - no array found in JSON");
            }

            int totalRecords = dataArray.size();
            log.info("Step 2: External data fetched successfully, {} email records to process", totalRecords);

            // CRITICAL: Fix connection leak - get BMM event once before processing to avoid repeated queries
            log.info("Step 3: Getting BMM event for data processing...");
            Event bmmEvent = getBMMEvent();
            if (bmmEvent == null) {
                log.error("No active BMM event found! Creating default BMM event...");
                bmmEvent = createDefaultBMMEvent();
            }
            log.info("Step 4: BMM event ready: {} (ID: {})", bmmEvent.getName(), bmmEvent.getId());

            // CRITICAL: Optimization - reduce batch size for large datasets to avoid 500 errors
            int batchSize = 500; // Reduce batch size to 500 records to reduce database and memory pressure
            int progressReportInterval = 2500; // Progress report every 2500 records
            int processedCount = 0;
            int errorCount = 0;

            log.info("Starting large dataset processing: {} records with batch size {}", totalRecords, batchSize);

            for (int startIndex = 0; startIndex < totalRecords; startIndex += batchSize) {
                int endIndex = Math.min(startIndex + batchSize, totalRecords);

                try {
                    // CRITICAL: Fix connection leak - pass pre-fetched bmmEvent to avoid repeated queries in batch processing
                    int batchProcessedCount = applicationContext.getBean(InformerSyncService.class)
                            .processEmailMembersDirectBatch(dataArray, startIndex, endIndex, batchId, bmmEvent);
                    processedCount += batchProcessedCount;

                    log.info("Email batch {}-{} completed: {} processed (Total: {}/{})",
                            startIndex + 1, endIndex, batchProcessedCount, processedCount, totalRecords);
                } catch (Exception e) {
                    errorCount++;
                    log.error("Email batch {}-{} failed: {} (Errors: {})",
                            startIndex + 1, endIndex, e.getMessage(), errorCount);

                    // Stop processing if too many errors - increased tolerance to 20 batches
                    if (errorCount > 20) {
                        log.error("Too many errors ({}), stopping sync process", errorCount);
                        break;
                    }
                }

                // Give database and memory some breathing room
                if (startIndex + batchSize < totalRecords) {
                    try {
                        Thread.sleep(500); // Increase delay to 500ms for more database time
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Sync interrupted, stopping...");
                        break;
                    }
                }

                // Dynamic progress reporting for large datasets
                if (processedCount % progressReportInterval == 0 || processedCount == totalRecords) {
                    double progress = (double) processedCount / totalRecords * 100;
                    long estimatedRemainingMin = (long) ((totalRecords - processedCount) * 0.6 / 60); // Estimate remaining minutes
                    log.info("Email sync progress: {}/{} ({:.1f}%) - Est. remaining: {}min",
                            processedCount, totalRecords, progress, estimatedRemainingMin);
                }
            }

            log.info("Email members DIRECT sync completed! Processed: {}/{} EventMembers, Errors: {}, Batch ID: {}",
                    processedCount, totalRecords, errorCount, batchId);

        } catch (Exception e) {
            log.error("Email members DIRECT sync failed: {}", e.getMessage(), e);

            // CRITICAL: Provide specific guidance for data corruption issues
            if (e.getMessage().contains("severely corrupted data") || e.getMessage().contains("corruption")) {
                log.error("=== API DATA CORRUPTION DETECTED ===");
                log.error("The external API is returning corrupted binary data instead of valid JSON.");
                log.error("This is a server-side issue with the external data source that cannot be fixed by our system.");
                log.error("");
                log.error("IMMEDIATE ACTION REQUIRED:");
                log.error("1. Contact the external API provider to report the data corruption issue");
                log.error("2. Verify the API URL is correct: {}", "Check with API provider");
                log.error("3. Ask the API provider to check their server status and data export process");
                log.error("4. Do NOT attempt sync again until the API provider confirms the issue is resolved");
                log.error("");
                log.error("Expected data format should be JSON like:");
                log.error("[{{\"membershipNumber\": \"123456\", \"fore1\": \"John\", \"surname\": \"Doe\", ...}}]");
                log.error("=== END CORRUPTION REPORT ===");
            }

            throw new RuntimeException("Email members DIRECT sync failed: " + e.getMessage(), e);
        }
    }

    // CRITICAL: Support timeout setting data fetching method with enhanced error handling and retry
    private String fetchDataFromUrlWithTimeout(String fullUrl, int timeoutMillis) {
        int maxRetries = 3;
        int currentRetry = 0;

        while (currentRetry < maxRetries) {
            try {
                log.info("Fetching large dataset from URL with {}ms timeout (attempt {}/{}): {}",
                        timeoutMillis, currentRetry + 1, maxRetries, fullUrl);

                // Create RestTemplate with long timeout support - use modern method to avoid deprecation warnings
                RestTemplate longTimeoutRestTemplate = new RestTemplate();

                // Set timeout - use SimpleClientHttpRequestFactory
                org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                        new org.springframework.http.client.SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(timeoutMillis);
                factory.setReadTimeout(timeoutMillis);
                longTimeoutRestTemplate.setRequestFactory(factory);

                // CRITICAL: Use multiple encoding converters to handle different data types
                longTimeoutRestTemplate.getMessageConverters().clear();
                longTimeoutRestTemplate.getMessageConverters().add(0,
                        new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
                longTimeoutRestTemplate.getMessageConverters().add(1,
                        new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.ISO_8859_1));

                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.set("User-Agent", "ETU-Voting-System/1.0");
                // CRITICAL: Don't request compression to avoid corruption issues
                headers.set("Accept-Encoding", "identity");
                headers.set("Accept-Charset", "utf-8, iso-8859-1");

                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = longTimeoutRestTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.info("Successfully fetched {} characters of data", responseBody != null ? responseBody.length() : 0);

                    // CRITICAL: Validate response before cleaning
                    if (responseBody == null || responseBody.isEmpty()) {
                        throw new RuntimeException("Empty response received from API");
                    }

                    // CRITICAL: Early detection of severely corrupted data (temporarily disabled)
                    if (false && corruptionCheckEnabled) {
                        int corruptedCount = 0;
                        int sampleSize = Math.min(responseBody.length(), 5000);
                        for (int i = 0; i < sampleSize; i++) {
                            char c = responseBody.charAt(i);
                            if (c == 0 || c == 65533 || (c >= 1 && c <= 8) || (c >= 14 && c <= 31)) {
                                corruptedCount++;
                            }
                        }

                        double corruptionRatio = (double) corruptedCount / sampleSize;
                        if (corruptionRatio > 0.50) { // More than 50% corrupted in sample (reduced sensitivity)
                            log.error("API response is severely corrupted: {:.2f}% corruption in first {} chars",
                                    corruptionRatio * 100, sampleSize);
                            log.error("Sample of corrupted response: {}",
                                    responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
                            throw new RuntimeException(String.format(
                                    "API returned severely corrupted response (%.2f%% corruption). Contact API provider immediately.",
                                    corruptionRatio * 100));
                        }
                    }

                    // CRITICAL: Clean data before returning - remove control characters that cause JSON parsing errors
                    String cleanedData = cleanJsonData(responseBody);
                    if (cleanedData != null && !cleanedData.equals(responseBody)) {
                        log.info("Cleaned data: removed {} invalid characters", responseBody.length() - cleanedData.length());
                    }

                    return cleanedData;
                } else {
                    throw new RuntimeException("HTTP error: " + response.getStatusCode());
                }
            } catch (Exception e) {
                currentRetry++;
                String errorMsg = e.getMessage();

                // Check if this is a corruption error that shouldn't be retried
                if (errorMsg != null && errorMsg.contains("severely corrupted response")) {
                    log.error("=== API DATA CORRUPTION DETECTED ===");
                    log.error("The external API is returning corrupted binary data instead of valid JSON.");
                    log.error("This is a server-side issue with the external data source that cannot be fixed by our system.");
                    log.error("");
                    log.error("IMMEDIATE ACTION REQUIRED:");
                    log.error("1. Contact the external API provider to report the data corruption issue");
                    log.error("2. Verify the API URL is correct: Check with API provider");
                    log.error("3. Ask the API provider to check their server status and data export process");
                    log.error("4. Do NOT attempt sync again until the API provider confirms the issue is resolved");
                    log.error("");
                    log.error("Expected data format should be JSON like:");
                    log.error("[{{\"membershipNumber\": \"123456\", \"fore1\": \"John\", \"surname\": \"Doe\", ...}}]");
                    log.error("=== END CORRUPTION REPORT ===");
                    throw new RuntimeException("Failed to fetch data with timeout: " + errorMsg, e);
                }

                if (currentRetry >= maxRetries) {
                    log.error("Failed to fetch data from URL {} after {} attempts: {}", fullUrl, maxRetries, errorMsg);
                    throw new RuntimeException("Failed to fetch data with timeout after " + maxRetries + " attempts: " + errorMsg, e);
                } else {
                    log.warn("Attempt {}/{} failed for URL {}: {}. Retrying in 5 seconds...",
                            currentRetry, maxRetries, fullUrl, errorMsg);
                    try {
                        Thread.sleep(5000); // Wait 5 seconds before retry
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        // This should never be reached due to the throw in the catch block
        throw new RuntimeException("Unexpected end of retry loop");
    }


    // CRITICAL: Validate and clean JSON data - reject severely corrupted data
    private String cleanJsonData(String jsonData) {
        if (jsonData == null || jsonData.isEmpty()) {
            return jsonData;
        }

        try {
            log.debug("Starting JSON data validation for {} characters", jsonData.length());

            // CRITICAL: First try to validate if it's already valid JSON
            try {
                objectMapper.readTree(jsonData);
                log.info("JSON data is already valid, no cleaning needed");
                return jsonData;
            } catch (Exception e) {
                log.debug("JSON validation failed, proceeding with analysis: {}", e.getMessage());
            }

            // CRITICAL: Check data quality before attempting to clean
            int corruptedChars = 0;
            int sampleSize = Math.min(jsonData.length(), 10000); // Sample first 10k chars

            for (int i = 0; i < sampleSize; i++) {
                char c = jsonData.charAt(i);
                // Count severely problematic characters
                if (c == 0 || c == 65533 || (c >= 1 && c <= 8) || (c >= 14 && c <= 31)) {
                    corruptedChars++;
                }
            }

            double corruptionRatio = (double) corruptedChars / sampleSize;

            // CRITICAL: If more than 1% of sampled data is corrupted, reject it
            if (corruptionRatio > 0.01) {
                log.error("API data is severely corrupted: {:.2f}% corruption detected ({} bad chars in {} sample)",
                        corruptionRatio * 100, corruptedChars, sampleSize);
                log.error("Data sample (first 500 chars): {}",
                        jsonData.length() > 500 ? jsonData.substring(0, 500) : jsonData);
                throw new RuntimeException(String.format(
                        "API returned severely corrupted data (%.2f%% corruption). This indicates a server-side issue with the external API.",
                        corruptionRatio * 100));
            }

            // If corruption is minimal, attempt cleaning
            StringBuilder cleaned = new StringBuilder();
            int removedChars = 0;

            for (int i = 0; i < jsonData.length(); i++) {
                char c = jsonData.charAt(i);

                // Remove only specific problematic characters
                if (c == 0 || c == 65533 || (c >= 1 && c <= 8) || (c >= 14 && c <= 31)) {
                    removedChars++;
                } else {
                    cleaned.append(c);
                }
            }

            String result = cleaned.toString().trim();

            if (removedChars > 0) {
                log.info("JSON cleaning completed. Original: {} chars, Cleaned: {} chars, Removed: {} chars",
                        jsonData.length(), result.length(), removedChars);
            }

            // CRITICAL: Find valid JSON start if needed
            if (result.length() > 0) {
                char firstChar = result.charAt(0);
                if (firstChar != '{' && firstChar != '[') {
                    int jsonStartIndex = -1;
                    for (int i = 0; i < Math.min(result.length(), 1000); i++) {
                        char c = result.charAt(i);
                        if (c == '{' || c == '[') {
                            jsonStartIndex = i;
                            break;
                        }
                    }

                    if (jsonStartIndex > 0) {
                        result = result.substring(jsonStartIndex);
                        log.info("Trimmed {} leading characters to start with valid JSON", jsonStartIndex);
                    } else {
                        throw new RuntimeException("No valid JSON structure found in cleaned data");
                    }
                }
            }

            // CRITICAL: Final validation - try to parse the cleaned data
            try {
                objectMapper.readTree(result);
                log.info("Cleaned data is now valid JSON");
                return result;
            } catch (Exception e) {
                log.error("Cleaned data is still not valid JSON: {}", e.getMessage());
                throw new RuntimeException("Unable to produce valid JSON from API response: " + e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process JSON data: {}", e.getMessage());
            throw new RuntimeException("JSON data processing failed: " + e.getMessage(), e);
        }
    }



    // Direct processing of Email member batches to EventMember
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    public int processEmailMembersDirectBatch(JsonNode dataArray, int startIndex, int endIndex, String batchId, Event bmmEvent) {
        List<EventMember> eventMembersToSave = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            JsonNode memberNode = dataArray.get(i);
            try {
                EventMember eventMember = createEventMemberFromInformerData(memberNode, "INFORMER_EMAIL_DIRECT", batchId, bmmEvent);
                if (eventMember != null) {
                    eventMembersToSave.add(eventMember);
                }
            } catch (Exception e) {
                log.error("Failed to process email member record {} directly: {}", i + 1, e.getMessage());
            }
        }

        if (!eventMembersToSave.isEmpty()) {
            eventMemberRepository.saveAll(eventMembersToSave);
            log.info("Saved {} EventMembers directly from email data", eventMembersToSave.size());
            return eventMembersToSave.size();
        }
        return 0;
    }

    // CRITICAL: Direct sync SMS members to EventMember table
    public void syncSmsMembersDirectlyToEventMember(String url) {
        log.info("Starting DIRECT SMS members sync to EventMember from: {}", url);
        String batchId = UUID.randomUUID().toString();

        try {
            // CRITICAL: Fix connection leak - fetch external data first to avoid long waits during database connection
            log.info("Step 1: Fetching SMS data from external API...");
            String jsonData = fetchDataFromUrlWithTimeout(url, 600000); // Use 10 minutes timeout consistent with email sync
            if (jsonData == null || jsonData.trim().isEmpty()) {
                throw new RuntimeException("No data received from URL");
            }

            // CRITICAL: Additional validation for data integrity
            if (jsonData.length() < 100) {
                log.error("Received unexpectedly small response ({} chars), data may be corrupted", jsonData.length());
                throw new RuntimeException("Response too small, possible data corruption");
            }

            // CRITICAL: Debug and validate JSON structure before parsing
            log.info("Attempting to parse cleaned JSON data. First 200 characters: {}",
                    jsonData.length() > 200 ? jsonData.substring(0, 200) + "..." : jsonData);

            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(jsonData);
            } catch (Exception e) {
                log.error("Failed to parse JSON. Error: {}. First 500 chars: {}",
                        e.getMessage(),
                        jsonData.length() > 500 ? jsonData.substring(0, 500) + "..." : jsonData);
                throw new RuntimeException("JSON parsing failed: " + e.getMessage(), e);
            }

            JsonNode dataArray;
            if (rootNode.isArray()) {
                log.info("Root node is an array with {} elements", rootNode.size());
                dataArray = rootNode;
            } else if (rootNode.isObject()) {
                log.info("Root node is an object. Looking for 'data' field...");
                dataArray = rootNode.get("data");
                if (dataArray == null) {
                    // Try other common field names
                    dataArray = rootNode.get("results");
                    if (dataArray == null) {
                        dataArray = rootNode.get("records");
                    }
                    if (dataArray == null) {
                        dataArray = rootNode.get("items");
                    }
                }
                if (dataArray != null) {
                    log.info("Found data array in '{}' field with {} elements",
                            dataArray == rootNode.get("data") ? "data" :
                                    dataArray == rootNode.get("results") ? "results" :
                                            dataArray == rootNode.get("records") ? "records" : "items",
                            dataArray.size());
                }
            } else {
                log.error("Root node is neither array nor object. Node type: {}", rootNode.getNodeType());
                throw new RuntimeException("Invalid JSON root structure - not array or object");
            }

            if (dataArray == null || !dataArray.isArray()) {
                log.error("No valid data array found. Root object available field names: {}",
                        rootNode.isObject() ? rootNode.fieldNames() : "Not an object");
                throw new RuntimeException("Invalid data array structure - no array found in JSON");
            }

            int totalRecords = dataArray.size();
            log.info("Step 2: External data fetched successfully, {} SMS records to process", totalRecords);

            // CRITICAL: Fix connection leak - get BMM event only when processing data to avoid long connection holds
            log.info("Step 3: Getting BMM event for data processing...");
            Event bmmEvent = getBMMEvent();
            if (bmmEvent == null) {
                log.error("No active BMM event found! Creating default BMM event...");
                bmmEvent = createDefaultBMMEvent();
            }
            log.info("Step 4: BMM event ready: {}", bmmEvent.getName());

            // CRITICAL: Optimization - batch processing for large SMS sync datasets
            int batchSize = 1000; // Increase batch size to 1000 records
            int progressReportInterval = 2500; // Progress report every 2500 records
            int processedCount = 0;
            int errorCount = 0;

            log.info("Starting large SMS dataset processing: {} records with batch size {}", totalRecords, batchSize);

            for (int startIndex = 0; startIndex < totalRecords; startIndex += batchSize) {
                int endIndex = Math.min(startIndex + batchSize, totalRecords);

                try {
                    int batchProcessedCount = applicationContext.getBean(InformerSyncService.class)
                            .processSmsMembersDirectBatch(dataArray, startIndex, endIndex, batchId, bmmEvent);
                    processedCount += batchProcessedCount;

                    log.info("SMS batch {}-{} completed: {} processed (Total: {}/{})",
                            startIndex + 1, endIndex, batchProcessedCount, processedCount, totalRecords);
                } catch (Exception e) {
                    errorCount++;
                    log.error("SMS batch {}-{} failed: {} (Errors: {})",
                            startIndex + 1, endIndex, e.getMessage(), errorCount);

                    // Stop processing if too many errors
                    if (errorCount > 20) {
                        log.error("Too many SMS errors ({}), stopping sync process", errorCount);
                        break;
                    }
                }

                // Give database some breathing room
                if (startIndex + batchSize < totalRecords) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("SMS sync interrupted, stopping...");
                        break;
                    }
                }

                // Dynamic progress reporting
                if (processedCount % progressReportInterval == 0 || processedCount == totalRecords) {
                    double progress = (double) processedCount / totalRecords * 100;
                    long estimatedRemainingMin = (long) ((totalRecords - processedCount) * 0.6 / 60);
                    log.info("SMS sync progress: {}/{} ({:.1f}%) - Est. remaining: {}min",
                            processedCount, totalRecords, progress, estimatedRemainingMin);
                }
            }

            log.info("SMS members DIRECT sync completed! Processed: {}/{} EventMembers, Errors: {}, Batch ID: {}",
                    processedCount, totalRecords, errorCount, batchId);

        } catch (Exception e) {
            log.error("SMS members DIRECT sync failed: {}", e.getMessage(), e);

            // CRITICAL: Provide specific guidance for data corruption issues
            if (e.getMessage().contains("severely corrupted data") || e.getMessage().contains("corruption")) {
                log.error("=== API DATA CORRUPTION DETECTED ===");
                log.error("The external API is returning corrupted binary data instead of valid JSON.");
                log.error("This is a server-side issue with the external data source that cannot be fixed by our system.");
                log.error("");
                log.error("IMMEDIATE ACTION REQUIRED:");
                log.error("1. Contact the external API provider to report the data corruption issue");
                log.error("2. Verify the API URL is correct: {}", "Check with API provider");
                log.error("3. Ask the API provider to check their server status and data export process");
                log.error("4. Do NOT attempt sync again until the API provider confirms the issue is resolved");
                log.error("");
                log.error("Expected data format should be JSON like:");
                log.error("[{{\"membershipNumber\": \"123456\", \"fore1\": \"John\", \"surname\": \"Doe\", ...}}]");
                log.error("=== END CORRUPTION REPORT ===");
            }

            throw new RuntimeException("SMS members DIRECT sync failed: " + e.getMessage(), e);
        }
    }

    // CRITICAL: SMS batch processing method
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 300)
    public int processSmsMembersDirectBatch(JsonNode dataArray, int startIndex, int endIndex, String batchId, Event bmmEvent) {
        List<EventMember> eventMembersToSave = new ArrayList<>();

        for (int i = startIndex; i < endIndex; i++) {
            JsonNode memberNode = dataArray.get(i);
            try {
                EventMember eventMember = createEventMemberFromInformerData(memberNode, "INFORMER_SMS_DIRECT", batchId, bmmEvent);
                if (eventMember != null) {
                    eventMembersToSave.add(eventMember);
                }
            } catch (Exception e) {
                log.error("Failed to process SMS member record {} directly: {}", i + 1, e.getMessage());
            }
        }

        if (!eventMembersToSave.isEmpty()) {
            eventMemberRepository.saveAll(eventMembersToSave);
            log.info("Saved {} SMS EventMembers in batch {}-{}", eventMembersToSave.size(), startIndex + 1, endIndex);
            return eventMembersToSave.size();
        }
        return 0;
    }

    // CRITICAL: Create EventMember record directly from Informer data
    private EventMember createEventMemberFromInformerData(JsonNode memberNode, String dataSource, String batchId, Event bmmEvent) {
        try {
            String membershipNumber = getJsonValue(memberNode, "membershipNumber");
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Missing membership number, skipping record. Available field count: {}",
                        memberNode.size());
                return null;
            }

            log.debug("Processing member {}: {} {}", membershipNumber,
                    getJsonValue(memberNode, "fore1"), getJsonValue(memberNode, "surname"));

            // Check if already exists
            Optional<EventMember> existingEventMember = eventMemberRepository.findByEventAndMembershipNumber(
                    bmmEvent, membershipNumber);

            if (existingEventMember.isPresent()) {
                log.debug("EventMember already exists for membership number: {}, updating", membershipNumber);
                EventMember eventMember = existingEventMember.get();
                updateEventMemberFromInformerData(eventMember, memberNode, dataSource, batchId);
                return eventMember;
            }

            // Create new EventMember record
            String primaryEmail = getJsonValue(memberNode, "primaryEmail");
            String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");

            // Build name
            String fore1 = getJsonValue(memberNode, "fore1");
            String surname = getJsonValue(memberNode, "surname");
            String name = buildMemberNameFromNode(fore1, surname, membershipNumber);

            EventMember eventMember = EventMember.builder()
                    .event(bmmEvent)
                    .member(null) // Do not associate with Member record
                    .membershipNumber(membershipNumber)
                    .name(name)
                    .primaryEmail(primaryEmail)
                    .telephoneMobile(telephoneMobile)
                    .hasEmail(primaryEmail != null && !primaryEmail.trim().isEmpty() && !primaryEmail.contains("@temp-email.etu.nz"))
                    .hasMobile(telephoneMobile != null && !telephoneMobile.trim().isEmpty())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .checkedIn(false)
                    // CRITICAL: Map all classification fields directly from Informer data
                    .fore1(fore1)
                    .knownAs(getJsonValue(memberNode, "knownAs"))
                    .surname(surname)
                    .dob(getJsonArrayFirstValue(memberNode, "dob"))
                    .ageOfMember(getJsonValue(memberNode, "ageOfMember"))
                    .genderDesc(getJsonValue(memberNode, "genderDesc"))
                    .ethnicRegionDesc(getJsonValue(memberNode, "ethnicRegionDesc"))
                    .ethnicOriginDesc(getJsonValue(memberNode, "ethnicOriginDesc"))
                    .employmentStatus(getJsonValue(memberNode, "employmentStatus"))
                    .payrollNumber(getJsonValue(memberNode, "payrollNumber"))
                    .siteCode(getJsonValue(memberNode, "siteCode"))
                    .siteIndustryDesc(getJsonValue(memberNode, "siteIndustryDesc"))
                    .siteSubIndustryDesc(getJsonValue(memberNode, "siteSubIndustryDesc"))
                    .membershipTypeDesc(getJsonValue(memberNode, "membershipTypeDesc"))
                    .bargainingGroupDesc(getJsonValue(memberNode, "bargainingGroupDesc"))
                    .workplaceDesc(getJsonArrayFirstValue(memberNode, "workplaceDesc"))
                    .sitePrimOrgName(getJsonValue(memberNode, "sitePrimOrgName"))
                    .orgTeamPDescEpmu(getJsonValue(memberNode, "orgTeamPDescEpmu"))
                    .directorName(getJsonValue(memberNode, "directorName"))
                    .subIndSector(getJsonValue(memberNode, "subIndSector"))
                    .jobTitle(getJsonValue(memberNode, "jobTitle"))
                    .department(getJsonValue(memberNode, "department"))
                    .location(getJsonValue(memberNode, "location"))
                    .phoneHome(getJsonValue(memberNode, "phoneHome"))
                    .phoneWork(getJsonValue(memberNode, "phoneWork"))
                    .address(getJsonValue(memberNode, "address"))
                    .regionDesc(getJsonValue(memberNode, "regionDesc"))
                    .region(getJsonValue(memberNode, "regionDesc"))
                    .branch(getJsonArrayFirstValue(memberNode, "branchDesc"))
                    .bargainingGroup(getJsonValue(memberNode, "bargainingGroupDesc"))
                    .workplace(getJsonArrayFirstValue(memberNode, "workplaceDesc"))
                    .employer(getJsonArrayFirstValue(memberNode, "employerName"))
                    // CRITICAL: 新增：从Informer数据样例映射新字段
                    .financialIndicatorDescription(getJsonValue(memberNode, "financialIndicatorDescription"))
                    .employeeRef(getJsonValue(memberNode, "employeeRef"))
                    .addRes1(getJsonValue(memberNode, "addRes1"))
                    .addRes2(getJsonValue(memberNode, "addRes2"))
                    .addRes3(getJsonValue(memberNode, "addRes3"))
                    .addRes4(getJsonValue(memberNode, "addRes4"))
                    .addRes5(getJsonValue(memberNode, "addRes5"))
                    .addResPc(getJsonValue(memberNode, "addResPc"))
                    .occupation(getJsonValue(memberNode, "occupation"))
                    .forumDesc(getJsonValue(memberNode, "forumDesc"))
                    .lastPaymentDate(getJsonValue(memberNode, "lastPaymentDate"))
                    .epmuMemTypeDesc(getJsonValue(memberNode, "epmuMemTypeDesc"))
                    .dataSource(dataSource)
                    .importBatchId(batchId)
                    .registrationStatus("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            log.debug("Successfully created EventMember for membership {}: {} - Email: {}, Mobile: {}",
                    membershipNumber, name, primaryEmail, telephoneMobile);
            return eventMember;

        } catch (Exception e) {
            String membershipNumber = memberNode != null ? getJsonValue(memberNode, "membershipNumber") : "unknown";
            log.error("Failed to create EventMember from Informer data for membership {}: {}. Data source: {}, Batch ID: {}",
                    membershipNumber, e.getMessage(), dataSource, batchId, e);
            return null;
        }
    }

    // 更新现有EventMember记录
    private void updateEventMemberFromInformerData(EventMember eventMember, JsonNode memberNode, String dataSource, String batchId) {
        String primaryEmail = getJsonValue(memberNode, "primaryEmail");
        String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");

        // 更新基本信息和姓名
        String fore1 = getJsonValue(memberNode, "fore1");
        String surname = getJsonValue(memberNode, "surname");
        String name = buildMemberNameFromNode(fore1, surname, eventMember.getMembershipNumber());

        eventMember.setName(name);
        eventMember.setPrimaryEmail(primaryEmail);
        eventMember.setTelephoneMobile(telephoneMobile);
        eventMember.setHasEmail(primaryEmail != null && !primaryEmail.trim().isEmpty() && !primaryEmail.contains("@temp-email.etu.nz"));
        eventMember.setHasMobile(telephoneMobile != null && !telephoneMobile.trim().isEmpty());

        // 更新个人基本信息字段
        eventMember.setFore1(fore1);
        eventMember.setKnownAs(getJsonValue(memberNode, "knownAs"));
        eventMember.setSurname(surname);
        eventMember.setDob(getJsonArrayFirstValue(memberNode, "dob"));
        eventMember.setAgeOfMember(getJsonValue(memberNode, "ageOfMember"));
        eventMember.setGenderDesc(getJsonValue(memberNode, "genderDesc"));
        eventMember.setEthnicRegionDesc(getJsonValue(memberNode, "ethnicRegionDesc"));
        eventMember.setEthnicOriginDesc(getJsonValue(memberNode, "ethnicOriginDesc"));

        // 更新工作相关字段
        eventMember.setEmploymentStatus(getJsonValue(memberNode, "employmentStatus"));
        eventMember.setPayrollNumber(getJsonValue(memberNode, "payrollNumber"));
        eventMember.setSiteCode(getJsonValue(memberNode, "siteCode"));
        eventMember.setSiteIndustryDesc(getJsonValue(memberNode, "siteIndustryDesc"));
        eventMember.setSiteSubIndustryDesc(getJsonValue(memberNode, "siteSubIndustryDesc"));
        eventMember.setMembershipTypeDesc(getJsonValue(memberNode, "membershipTypeDesc"));
        eventMember.setBargainingGroupDesc(getJsonValue(memberNode, "bargainingGroupDesc"));
        eventMember.setWorkplaceDesc(getJsonArrayFirstValue(memberNode, "workplaceDesc"));
        eventMember.setSitePrimOrgName(getJsonValue(memberNode, "sitePrimOrgName"));
        eventMember.setOrgTeamPDescEpmu(getJsonValue(memberNode, "orgTeamPDescEpmu"));
        eventMember.setDirectorName(getJsonValue(memberNode, "directorName"));
        eventMember.setSubIndSector(getJsonValue(memberNode, "subIndSector"));
        eventMember.setJobTitle(getJsonValue(memberNode, "jobTitle"));
        eventMember.setDepartment(getJsonValue(memberNode, "department"));
        eventMember.setLocation(getJsonValue(memberNode, "location"));

        // 更新联系方式
        eventMember.setPhoneHome(getJsonValue(memberNode, "phoneHome"));
        eventMember.setPhoneWork(getJsonValue(memberNode, "phoneWork"));
        eventMember.setAddress(getJsonValue(memberNode, "address"));

        // 更新地区信息
        eventMember.setRegionDesc(getJsonValue(memberNode, "regionDesc"));
        eventMember.setRegion(getJsonValue(memberNode, "regionDesc"));
        eventMember.setBranch(getJsonArrayFirstValue(memberNode, "branchDesc"));
        eventMember.setBargainingGroup(getJsonValue(memberNode, "bargainingGroupDesc"));
        eventMember.setWorkplace(getJsonArrayFirstValue(memberNode, "workplaceDesc"));
        eventMember.setEmployer(getJsonArrayFirstValue(memberNode, "employerName"));

        // CRITICAL: 新增：更新新字段
        eventMember.setFinancialIndicatorDescription(getJsonValue(memberNode, "financialIndicatorDescription"));
        eventMember.setEmployeeRef(getJsonValue(memberNode, "employeeRef"));
        eventMember.setAddRes1(getJsonValue(memberNode, "addRes1"));
        eventMember.setAddRes2(getJsonValue(memberNode, "addRes2"));
        eventMember.setAddRes3(getJsonValue(memberNode, "addRes3"));
        eventMember.setAddRes4(getJsonValue(memberNode, "addRes4"));
        eventMember.setAddRes5(getJsonValue(memberNode, "addRes5"));
        eventMember.setAddResPc(getJsonValue(memberNode, "addResPc"));
        eventMember.setOccupation(getJsonValue(memberNode, "occupation"));
        eventMember.setForumDesc(getJsonValue(memberNode, "forumDesc"));
        eventMember.setLastPaymentDate(getJsonValue(memberNode, "lastPaymentDate"));
        eventMember.setEpmuMemTypeDesc(getJsonValue(memberNode, "epmuMemTypeDesc"));

        eventMember.setDataSource(dataSource);
        eventMember.setImportBatchId(batchId);
        eventMember.setUpdatedAt(LocalDateTime.now());
    }

    //    同步仅短信的活跃会员 (数据源3)
    @Transactional
    public void syncSmsMembersData(String url) {
        log.info("CRITICAL: Redirecting to DIRECT EventMember sync (bypassing Member table)");
        // CRITICAL: Fixed - now directly call the direct sync method to ensure complete JSON data is saved to EventMember table
        syncSmsMembersDirectlyToEventMember(url);
    }

    //    处理完整的参会记录：自动创建Event、Member、EventMember
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCompleteAttendeeRecord(JsonNode attendeeNode, String batchId, int recordNum, Map<String, Integer> processedCounts) {
        String membershipNumber = null;
        try {
            // 1. 首先处理事件信息，自动创建或查找Event
            Event event = null;
            try {
                event = findOrCreateEventFromAttendeeNode(attendeeNode, batchId);
                if (event != null) {
                    synchronized(processedCounts) {
                        processedCounts.put("events", processedCounts.get("events") + 1);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to process event for record {}: {}", recordNum, e.getMessage());
                // Continue processing member even if event creation fails
            }

            // 2. 处理会员信息
            membershipNumber = getJsonValue(attendeeNode, "membershipNumber");
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Record {}: Missing membership number, skipping", recordNum);
                return;
            }

            Member member = null;
            try {
                member = findOrCreateMemberFromAttendee(attendeeNode, membershipNumber, batchId);
                if (member != null) {
                    synchronized(processedCounts) {
                        processedCounts.put("members", processedCounts.get("members") + 1);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process member {} for record {}: {}", membershipNumber, recordNum, e.getMessage());
                // Continue to next record if member processing fails
                return;
            }

            // 3. 创建EventMember关联（如果是BMM事件或包含事件信息）
            if (event != null && member != null && shouldCreateEventMemberRelation(attendeeNode, event)) {
                try {
                    createEventMemberFromAttendee(attendeeNode, event, member, batchId);
                    synchronized(processedCounts) {
                        processedCounts.put("eventMembers", processedCounts.get("eventMembers") + 1);
                    }
                } catch (Exception e) {
                    log.warn("Failed to create EventMember for record {}, member {}: {}",
                            recordNum, membershipNumber, e.getMessage());
                    // Don't throw exception for EventMember creation failures
                }
            }

        } catch (Exception e) {
            log.error("Failed to process complete attendee record {} (membershipNumber: {}): {}",
                    recordNum, membershipNumber, e.getMessage());
            // Don't re-throw the exception to avoid Session pollution
        }
    }


    //    从参会记录中提取完整事件信息并创建或查找Event + 基于用户提供的完整JSON数据结构
    private Event findOrCreateEventFromAttendeeNode(JsonNode attendeeNode, String batchId) {
        try {
            // Extract complete event information - based on user's latest provided JSON structure
            String eventId = getJsonArrayFirstValue(attendeeNode, "event"); // "369319565" - Most important Informer internal ID
            String etuEventCode = getJsonArrayFirstValue(attendeeNode, "etuEventCode"); // "AKL110625SUAWA"
            String eventType = getJsonNestedArrayValue(attendeeNode, "link_to_event_detail_assoc_xeventType"); // "Standing Up at Work - 711"
            String venue = getJsonNestedArrayValue(attendeeNode, "link_to_event_detail_assoc_xvenue"); // "E tu Auckland Office"
            String eventDateStr = getJsonNestedArrayValue(attendeeNode, "link_to_event_detail_assoc_date"); // "11/06/2025"

            // Attendance status information
            String delegateAttended = getJsonArrayFirstValue(attendeeNode, "delegateAttended"); // "N"
            String erelAttend = getJsonValue(attendeeNode, "erelAttend"); // "N"

            log.info("Processing event data - Internal Event ID: {} (PRIMARY), ETU Code: {}, Type: {}, Venue: {}, Date: {}",
                    eventId, etuEventCode, eventType, venue, eventDateStr);

            // If no most important eventId, return default event
            if (eventId == null || eventId.trim().isEmpty()) {
                log.warn("No internal event ID found in attendee record, using default event");
                return getTargetEventForImport(null);
            }

            // Priority: Find existing event by most important internal eventId
            Optional<Event> existingEvent = eventRepository.findByDatasetId("informer-event-" + eventId);
            if (existingEvent.isPresent()) {
                log.info("Found existing event by internal event ID: {}", eventId);
                return existingEvent.get();
            }

            // Alternative: Find by etuEventCode
            if (etuEventCode != null && !etuEventCode.trim().isEmpty()) {
                Optional<Event> existingEventByCode = eventRepository.findByEventCode(etuEventCode);
                if (existingEventByCode.isPresent()) {
                    log.info("Found existing event by ETU code: {}", etuEventCode);
                    return existingEventByCode.get();
                }
            }

            // Parse event date
            LocalDateTime eventDate = null;
            if (eventDateStr != null && !eventDateStr.trim().isEmpty()) {
                try {
                    // Handle "11/06/2025" format
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    LocalDate date = LocalDate.parse(eventDateStr, formatter);
                    eventDate = date.atTime(9, 0); // Default 9 AM
                    log.info("Parsed event date: {} -> {}", eventDateStr, eventDate);
                } catch (Exception e) {
                    log.warn("Failed to parse event date: {}", eventDateStr);
                }
            }

            // Build event name - use more complete information
            String eventName = eventType != null ? eventType : "Imported Event";
            if (etuEventCode != null) {
                eventName = eventName + " (" + etuEventCode + ")";
            }

            // Build description information
            StringBuilder description = new StringBuilder("Auto-created from Informer attendee data");
            if (eventId != null) {
                description.append(" - Internal Event ID: ").append(eventId);
            }
            if (delegateAttended != null) {
                description.append(" - Delegate Attended: ").append(delegateAttended);
            }
            if (erelAttend != null) {
                description.append(" - EREL Attend: ").append(erelAttend);
            }

            // Determine event type - intelligent judgment based on event name, create record for each specific event
            Event.EventType determinedEventType = Event.EventType.GENERAL_MEETING; // Default
            if (eventType != null) {
                String lowerEventType = eventType.toLowerCase();
                if (lowerEventType.contains("bmm") || lowerEventType.contains("biennial")) {
                    determinedEventType = Event.EventType.BMM_VOTING;
                } else if (lowerEventType.contains("ballot") || lowerEventType.contains("vote")) {
                    determinedEventType = Event.EventType.BALLOT_VOTING;
                } else if (lowerEventType.contains("conference")) {
                    determinedEventType = Event.EventType.SPECIAL_CONFERENCE;
                } else if (lowerEventType.contains("survey")) {
                    determinedEventType = Event.EventType.SURVEY_MEETING;
                } else if (lowerEventType.contains("workshop")) {
                    determinedEventType = Event.EventType.WORKSHOP;
                } else if (lowerEventType.contains("annual")) {
                    determinedEventType = Event.EventType.ANNUAL_MEETING;
                } else if (lowerEventType.contains("training")) {
                    determinedEventType = Event.EventType.WORKSHOP; // Treat training as workshop
                } else if (lowerEventType.contains("meeting")) {
                    determinedEventType = Event.EventType.GENERAL_MEETING;
                }
            }

            log.info("Determined event type: {} for event: {}", determinedEventType, eventName);

            // Create new event - use correct field priority
            Event newEvent = Event.builder()
                    .name(eventName)
                    .eventCode(etuEventCode != null ? etuEventCode : "EVENT_" + eventId) // Priority: use etuEventCode, fallback to eventId
                    .datasetId("informer-event-" + eventId) // Most important: use internal eventId as datasetId
                    .attendeeDatasetId("d382fc79-1230-4a1d-917a-7bc43aa21a84") // User provided attendee dataset ID
                    .description(description.toString())
                    .eventType(determinedEventType)
                    .eventDate(eventDate)
                    .venue(venue)
                    .isActive(true)
                    .isVotingEnabled(determinedEventType == Event.EventType.BMM_VOTING ||
                            determinedEventType == Event.EventType.BALLOT_VOTING)
                    .registrationOpen(true)
                    .qrScanEnabled(true) // Enable QR code scanning
                    .syncStatus(Event.SyncStatus.SUCCESS)
                    .memberSyncCount(0)
                    .attendeeSyncCount(0)
                    // Set Informer sync URL - use user's latest provided URL
                    .informerAttendeeUrl("https://etu-inf5-rsau.aptsolutions.net/api/datasets/d382fc79-1230-4a1d-917a-7bc43aa21a84/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiI2NjI4ZTNkZi1hZmZhLTQ3OWUtOWEzZC1iZTMwZjFhZjI5NzEiLCJpYXQiOjE3NDk2OTg4Nzk7MjQxfQ.ZR8WC1UbQAtV6r5EyNG083qzQ450pJb1HRze5wFgR51")
                    .autoSyncEnabled(false) // Don't auto-sync to avoid loops
                    .build();

            Event savedEvent = eventRepository.save(newEvent);
            log.info("Auto-created new event: '{}' (ID: {}, Code: {}, Type: {}, Date: {})",
                    savedEvent.getName(), savedEvent.getId(), savedEvent.getEventCode(),
                    savedEvent.getEventType(), savedEvent.getEventDate());

            return savedEvent;

        } catch (Exception e) {
            log.error("Failed to create event from attendee node: {}", e.getMessage(), e);
            // Return default event as fallback
            return getTargetEventForImport(null);
        }
    }

    private Event getTargetEventForImport(Long eventId) {
        if (eventId != null) {
            Optional<Event> specificEvent = eventRepository.findById(eventId);
            if (specificEvent.isPresent()) {
                return specificEvent.get();
            }
        }

//        如果没有指定事件ID，查找第一个活跃的BMM事件
        List<Event> bmmEvents = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc()
                .stream()
                .filter(e -> e.getEventType() == Event.EventType.BMM_VOTING)
                .collect(Collectors.toList());

        if (!bmmEvents.isEmpty()) {
            return bmmEvents.get(0); // 返回最新的BMM事件
        }

        throw new RuntimeException("No active BMM event found for import");
    }

    //    EventAttendee processing method removed - all data now handled through EventMember table
    private Member findOrCreateMemberFromAttendee(JsonNode attendeeNode, String membershipNumber, String batchId) {
        try {
//            Step 2.1: 判断会员信息是否在member表中存在
            Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber);

            if (existingMember.isPresent()) {
                log.debug("Found existing member for membership number: {}", membershipNumber);
                return existingMember.get();
            }

//            Step 2.2: 如果不存在，就要insert
            log.info("Creating new member for membership number: {}", membershipNumber);

            String firstName = getJsonValue(attendeeNode, "link_to_member_assoc_fore1");
            String lastName = getJsonValue(attendeeNode, "link_to_member_assoc_surname");
            String primaryEmail = getJsonValue(attendeeNode, "link_to_member_assoc_primaryEmail");
            String telephoneMobile = getJsonValue(attendeeNode, "link_to_member_assoc_telephoneMobile");

//            Build member name
            String memberName = buildMemberNameFromNode(firstName, lastName, membershipNumber);

//            Validate required fields
            if (memberName.equals("Unknown Member")) {
                log.warn("Member {} has no name information", membershipNumber);
            }

//            提取完整的会员信息 - 基于实际的Attendee JSON结构
            String siteIndustryDesc = getJsonValue(attendeeNode, "link_to_member_assoc_siteIndustryDesc"); // "Aviation", "Public & Commercial"
            String regionDesc = getJsonValue(attendeeNode, "link_to_member_assoc_regionDesc"); // "Northern"
            String branchDesc = getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_branchDesc"); // "Auckland"
            String employerName = getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_employerName"); // "Auckland International Airport Ltd"
            String workplaceDesc = getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_workplaceDesc"); // "Auckland Airport - Airport Emergency Services (AES)"

//            尝试从Event Attendee数据中获取更多字段
            String occupation = getJsonValue(attendeeNode, "link_to_member_assoc_occupation");
            String membershipTypeDesc = getJsonValue(attendeeNode, "link_to_member_assoc_membershipTypeDesc");
            String financialIndicator = getJsonValue(attendeeNode, "link_to_member_assoc_financialIndicatorDescription");
            String epmuMemTypeDesc = getJsonValue(attendeeNode, "link_to_member_assoc_epmuMemTypeDesc");
            String forumDesc = getJsonValue(attendeeNode, "link_to_member_assoc_forumDesc");
            String lastPaymentDate = getJsonValue(attendeeNode, "link_to_member_assoc_lastPaymentDate");
            String employeeRef = getJsonValue(attendeeNode, "link_to_member_assoc_employeeRef");
            String knownAs = getJsonValue(attendeeNode, "link_to_member_assoc_knownAs"); // CRITICAL: know as
            String dob = getJsonValue(attendeeNode, "link_to_member_assoc_dob"); // CRITICAL: dob

//            额外工作字段
            String employmentStatus = getJsonValue(attendeeNode, "link_to_member_assoc_employmentStatus");
            String siteCode = getJsonValue(attendeeNode, "link_to_member_assoc_siteCode"); // CRITICAL: site-code
            String payrollNumber = getJsonValue(attendeeNode, "link_to_member_assoc_payrollNumber");
            String location = getJsonValue(attendeeNode, "link_to_member_assoc_location");

//            地址字段 - 尝试从Event Attendee数据中获取
            String addRes1 = getJsonValue(attendeeNode, "link_to_member_assoc_addRes1");
            String addRes2 = getJsonValue(attendeeNode, "link_to_member_assoc_addRes2");
            String addRes3 = getJsonValue(attendeeNode, "link_to_member_assoc_addRes3");
            String addRes4 = getJsonValue(attendeeNode, "link_to_member_assoc_addRes4");
            String addRes5 = getJsonValue(attendeeNode, "link_to_member_assoc_addRes5");
            String addResPc = getJsonValue(attendeeNode, "link_to_member_assoc_addResPc");

            log.info("Creating member {} with profile: Industry={}, Region={}, Branch={}, Employer={}, Workplace={}, Occupation={}, MembershipType={}, Financial={}",
                    membershipNumber, siteIndustryDesc, regionDesc, branchDesc, employerName, workplaceDesc, occupation, membershipTypeDesc, financialIndicator);

//            DEBUG: Log email data processing
            log.info("Processing member {}: primaryEmail=[{}], telephoneMobile=[{}], isValidEmail={}",
                    membershipNumber, primaryEmail, telephoneMobile,
                    primaryEmail != null ? isValidEmail(primaryEmail.trim()) : false);

//            Only use real email address, no temp email generation
            String finalEmail = null;
            if (primaryEmail != null && !primaryEmail.trim().isEmpty() && isValidEmail(primaryEmail.trim())) {
                finalEmail = primaryEmail.trim();
                log.info("Member {}: Using primaryEmail as finalEmail: [{}]", membershipNumber, finalEmail);
            } else {
                log.info("Member {}: No valid email available, finalEmail will be null. primaryEmail=[{}]",
                        membershipNumber, primaryEmail);
            }

            Member newMember = Member.builder()
                    .membershipNumber(membershipNumber)
                    .name(memberName)
                    .primaryEmail(finalEmail) // Only real email, no temp email generation
                    .fore1(firstName)
                    .surname(lastName)
                    .knownAs(knownAs)
//                    行业和会员信息
                    .siteIndustryDesc(siteIndustryDesc)
                    .occupation(occupation)
                    .financialIndicator(financialIndicator)
                    .membershipTypeDesc(membershipTypeDesc)
                    .epmuMemTypeDesc(epmuMemTypeDesc)
                    .forumDesc(forumDesc)
                    .lastPaymentDate(lastPaymentDate)
                    .employeeRef(employeeRef)
                    .dob(dob)
//                    联系信息
                    .telephoneMobile(telephoneMobile)
                    .primaryEmail(primaryEmail)
//                    地址信息
                    .addRes1(addRes1)
                    .addRes2(addRes2)
                    .addRes3(addRes3)
                    .addRes4(addRes4)
                    .addRes5(addRes5)
                    .addResPc(addResPc)
//                    工作信息
                    .employerName(employerName)
                    .workplaceDesc(workplaceDesc)
//                    地理信息
                    .regionDesc(regionDesc)
                    .branchDesc(branchDesc)
//                    状态标志
                    .hasEmail(finalEmail != null && !finalEmail.trim().isEmpty())
                    .hasMobile(telephoneMobile != null && !telephoneMobile.trim().isEmpty())
//                    额外工作字段
                    .employmentStatus(employmentStatus)
                    .siteNumber(siteCode) // site-code
                    .payrollNumber(payrollNumber)
                    .location(location)
//                    Stratum同步所需的额外字段
                    .phoneHome(getJsonValue(attendeeNode, "link_to_member_assoc_telephoneHome"))
                    .phoneWork(getJsonValue(attendeeNode, "link_to_member_assoc_telephoneWork"))
                    .jobTitle(occupation) // 使用occupation作为jobTitle
                    .employer(employerName) // 使用employerName作为employer
                    .department(branchDesc) // 使用branchDesc作为department
//                    新增完整JSON字段映射 (基于事件参与者数据)
                    .ageOfMember(getJsonValue(attendeeNode, "link_to_member_assoc_ageOfMember"))
                    .genderDesc(getJsonValue(attendeeNode, "link_to_member_assoc_genderDesc"))
                    .ethnicRegionDesc(getJsonValue(attendeeNode, "link_to_member_assoc_ethnicRegionDesc"))
                    .ethnicOriginDesc(getJsonValue(attendeeNode, "link_to_member_assoc_ethnicOriginDesc"))
                    .siteSubIndustryDesc(getJsonValue(attendeeNode, "link_to_member_assoc_siteSubIndustryDesc"))
                    .bargainingGroupDesc(getJsonValue(attendeeNode, "link_to_member_assoc_bargainingGroupDesc"))
                    .sitePrimOrgName(getJsonValue(attendeeNode, "link_to_member_assoc_sitePrimOrgName"))
                    .orgTeamPDescEpmu(getJsonValue(attendeeNode, "link_to_member_assoc_orgTeamPDescEpmu"))
                    .directorName(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_link_to_sites_assoc_directorName"))
                    .subIndSector(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_link_to_workplace_assoc_xsubIndSector"))
//                    系统字段
                    .dataSource("INFORMER_AUTO_CREATED")
                    .syncStatus("SUCCESS")
                    .importBatchId(batchId)
                    .lastSyncTime(LocalDateTime.now())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
//                    初始状态
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .build();

//            处理Stratum所需的复合字段

//            组合地址字段用于Stratum
            StringBuilder addressBuilder = new StringBuilder();
            if (newMember.getAddRes1() != null) addressBuilder.append(newMember.getAddRes1());
            if (newMember.getAddRes2() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(newMember.getAddRes2());
            }
            if (newMember.getAddRes3() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(newMember.getAddRes3());
            }
            if (newMember.getAddRes4() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(newMember.getAddRes4());
            }
            if (newMember.getAddRes5() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(newMember.getAddRes5());
            }
            if (newMember.getAddResPc() != null) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(newMember.getAddResPc());
            }

            if (addressBuilder.length() > 0) {
                newMember.setAddress(addressBuilder.toString());
            }

//            生日字段转换用于Stratum
            if (newMember.getDob() != null && !newMember.getDob().trim().isEmpty()) {
                try {
                    String dobStr = newMember.getDob().trim();
//                    尝试解析DD/MM/YYYY格式
                    if (dobStr.contains("/")) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        LocalDate parsedDob = LocalDate.parse(dobStr, formatter);
                        newMember.setDobLegacy(parsedDob);
                    }
//                    尝试解析YYYY-MM-DD格式
                    else if (dobStr.contains("-")) {
                        LocalDate parsedDob = LocalDate.parse(dobStr);
                        newMember.setDobLegacy(parsedDob);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse DOB for Stratum sync: {} (member: {})", newMember.getDob(), membershipNumber);
                }
            }

            Member savedMember = memberRepository.save(newMember);
            log.info("Successfully created new member: {} (ID: {})", membershipNumber, savedMember.getId());
            return savedMember;

        } catch (Exception e) {
            log.error("Failed to find or create member for membership {}: {}", membershipNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to find or create member for membership: " + membershipNumber, e);
        }
    }

    private String buildMemberNameFromNode(String firstName, String lastName, String membershipNumber) {
        if (firstName != null && !firstName.trim().isEmpty() && lastName != null && !lastName.trim().isEmpty()) {
            return (firstName.trim() + " " + lastName.trim()).trim();
        } else if (firstName != null && !firstName.trim().isEmpty()) {
            return firstName.trim();
        } else if (lastName != null && !lastName.trim().isEmpty()) {
            return lastName.trim();
        }
        return "Member " + membershipNumber; // Fallback with membership number
    }

    private String generateTempEmail(String membershipNumber) {
        return "member-" + membershipNumber + "@temp-email.etu.nz";
    }

    //    为BMM事件创建EventMember记录
    private void createEventMemberFromAttendee(JsonNode attendeeNode, Event event, Member member, String batchId) {
        try {
//            Check if EventMember already exists
            Optional<EventMember> existingEventMember = eventMemberRepository.findByEventAndMembershipNumber(
                    event, member.getMembershipNumber());

            if (existingEventMember.isPresent()) {
//                如果EventMember已存在，更新信息但保持token不变
                EventMember eventMember = existingEventMember.get();
                log.debug("EventMember already exists for member {} in event {}, updating info while keeping token stable",
                        member.getMembershipNumber(), event.getName());

//                更新基本信息
                eventMember.setName(member.getName());
                eventMember.setMember(member);

//                更新联系信息
                String primaryEmail = getJsonValue(attendeeNode, "link_to_member_assoc_primaryEmail");
                String telephoneMobile = getJsonValue(attendeeNode, "link_to_member_assoc_telephoneMobile");

                String eventMemberEmail = null;
                if (primaryEmail != null && !primaryEmail.trim().isEmpty() && isValidEmail(primaryEmail.trim())) {
                    eventMemberEmail = primaryEmail.trim();
                } else if (member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty()) {
                    eventMemberEmail = member.getPrimaryEmail();
                }

                eventMember.setPrimaryEmail(eventMemberEmail);
                eventMember.setTelephoneMobile(telephoneMobile);
                eventMember.setHasEmail(eventMemberEmail != null && !eventMemberEmail.trim().isEmpty());
                eventMember.setHasMobile(telephoneMobile != null && !telephoneMobile.trim().isEmpty());

//                更新工作信息
                eventMember.setRegionDesc(getJsonValue(attendeeNode, "link_to_member_assoc_regionDesc"));
                eventMember.setRegion(getJsonValue(attendeeNode, "link_to_member_assoc_regionDesc"));
                eventMember.setWorkplace(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_workplaceDesc"));
                eventMember.setEmployer(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_employerName"));
                eventMember.setBranch(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_branchDesc"));

//                更新同步信息
                eventMember.setImportBatchId(batchId);
                eventMember.setUpdatedAt(LocalDateTime.now());

                eventMemberRepository.save(eventMember);
                log.debug("Updated existing EventMember for member {} in BMM event {} while preserving token",
                        member.getMembershipNumber(), event.getName());
                return;
            }

            String primaryEmail = getJsonValue(attendeeNode, "link_to_member_assoc_primaryEmail");
            String telephoneMobile = getJsonValue(attendeeNode, "link_to_member_assoc_telephoneMobile");

//            Determine final email value for EventMember - allow null if no valid email
            String eventMemberEmail = null;
            if (primaryEmail != null && !primaryEmail.trim().isEmpty() && isValidEmail(primaryEmail.trim())) {
                eventMemberEmail = primaryEmail.trim();
            } else if (member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty()) {
                eventMemberEmail = member.getPrimaryEmail();
            }
//            If no valid email, leave as null
//            只有新创建的EventMember才生成新token
            EventMember eventMember = EventMember.builder()
                    .event(event)
                    .member(member)
                    .membershipNumber(member.getMembershipNumber())
                    .name(member.getName())
                    .primaryEmail(eventMemberEmail) // Allow null email
                    .telephoneMobile(telephoneMobile)
                    .hasEmail(eventMemberEmail != null && !eventMemberEmail.trim().isEmpty())
                    .hasMobile(telephoneMobile != null && !telephoneMobile.trim().isEmpty())
                    .token(UUID.randomUUID()) // 只有新创建时才生成token
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .checkedIn(false)
                    .regionDesc(getJsonValue(attendeeNode, "link_to_member_assoc_regionDesc"))
                    .region(getJsonValue(attendeeNode, "link_to_member_assoc_regionDesc"))
                    .workplace(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_workplaceDesc"))
                    .employer(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_employerName"))
                    .branch(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_branchDesc"))
                    .bargainingGroup(getJsonArrayFirstValue(attendeeNode, "link_to_member_assoc_bargainingGroupDesc"))
                    // CRITICAL: 新增：重要的分类字段
                    .siteIndustryDesc(member.getSiteIndustryDesc()) // 从Member同步行业分类
                    .membershipTypeDesc(member.getMembershipTypeDesc()) // 从Member同步会员类型
                    .bargainingGroupDesc(member.getBargainingGroupDesc()) // 从Member同步谈判组描述
                    .siteSubIndustryDesc(member.getSiteSubIndustryDesc()) // 从Member同步细分行业
                    .genderDesc(member.getGenderDesc()) // 从Member同步性别
                    .ageOfMember(member.getAgeOfMember()) // 从Member同步年龄
                    .ethnicRegionDesc(member.getEthnicRegionDesc()) // 从Member同步民族地区
                    .employmentStatus(member.getEmploymentStatus()) // 从Member同步就业状态
                    .dataSource("INFORMER_ATTENDEE")
                    .importBatchId(batchId)
                    .registrationStatus("PENDING")
                    .createdAt(LocalDateTime.now())
                    .build();

            eventMemberRepository.save(eventMember);
            log.debug("Created NEW EventMember for member {} in BMM event {} with fresh token",
                    member.getMembershipNumber(), event.getName());

        } catch (Exception e) {
            log.error("Failed to create EventMember for member {} in event {}: {}",
                    member.getMembershipNumber(), event.getName(), e.getMessage());
            throw e;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Member processFinancialDeclarationMember(JsonNode memberNode, String dataSource, String batchId, int recordNum) {
        try {
            String membershipNumber = getJsonValue(memberNode, "membershipNumber");
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Record {}: Missing membership number, skipping", recordNum);
                return null;
            }

            Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber);
            Member member;

            if (existingMember.isPresent()) {
//                使用现有成员，保持token和验证码不变
                member = existingMember.get();
                log.debug("Using existing member with stable token: {}", membershipNumber);
            } else {
//                只有新成员才生成token和验证码
                member = Member.builder()
                        .membershipNumber(membershipNumber)
                        .name("Member " + membershipNumber) // Set default name to avoid null constraint violation
                        .token(UUID.randomUUID())
                        .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                        .hasRegistered(false)
                        .isAttending(false)
                        .isSpecialVote(false)
                        .hasVoted(false)
                        .build();
                log.debug("Creating new member with fresh token: {}", membershipNumber);
            }

//            更新会员信息
            member.setFinancialIndicator(getJsonValue(memberNode, "financialIndicatorDescription")); // CRITICAL: 使用JSON驼峰格式
            member.setFore1(getJsonValue(memberNode, "fore1"));
            member.setKnownAs(getJsonValue(memberNode, "knownAs")); // CRITICAL: 使用JSON驼峰格式
            member.setSurname(getJsonValue(memberNode, "surname"));
            member.setDob(getJsonArrayFirstValue(memberNode, "dob"));
            member.setEmployeeRef(getJsonValue(memberNode, "employeeRef")); // CRITICAL: 使用JSON驼峰格式
            member.setSiteIndustryDesc(getJsonValue(memberNode, "siteIndustryDesc")); // CRITICAL: 使用JSON驼峰格式
            member.setOccupation(getJsonValue(memberNode, "occupation"));

//            地址信息 - CRITICAL: 使用JSON驼峰格式
            member.setAddRes1(getJsonValue(memberNode, "addRes1"));
            member.setAddRes2(getJsonValue(memberNode, "addRes2"));
            member.setAddRes3(getJsonValue(memberNode, "addRes3"));
            member.setAddRes4(getJsonValue(memberNode, "addRes4"));
            member.setAddRes5(getJsonValue(memberNode, "addRes5"));
            member.setAddResPc(getJsonValue(memberNode, "addResPc"));

//            联系信息 - CRITICAL: 使用JSON驼峰格式
            member.setTelephoneMobile(getJsonValue(memberNode, "telephoneMobile"));
            member.setPrimaryEmail(getJsonValue(memberNode, "primaryEmail"));

//            工作信息 - 完整映射
            member.setEmployerName(getJsonArrayFirstValue(memberNode, "employerName"));
            member.setWorkplaceDesc(getJsonArrayFirstValue(memberNode, "workplaceDesc"));
            member.setRegionDesc(getJsonValue(memberNode, "regionDesc")); // CRITICAL: 使用JSON驼峰格式
            member.setBranchDesc(getJsonArrayFirstValue(memberNode, "branchDesc")); // CRITICAL: 使用JSON驼峰格式
            member.setForumDesc(getJsonValue(memberNode, "forumDesc")); // CRITICAL: 使用JSON驼峰格式

//            注意：以下字段在Financial Declaration数据源中不存在，只在Event Attendees中存在
//            只有当字段确实存在时才映射，避免覆盖现有数据
            if (memberNode.has("employmentStatus")) {
                member.setEmploymentStatus(getJsonValue(memberNode, "employmentStatus"));
            }
            if (memberNode.has("siteCode")) {
                member.setSiteNumber(getJsonValue(memberNode, "siteCode"));
            }
            if (memberNode.has("payrollNumber")) {
                member.setPayrollNumber(getJsonValue(memberNode, "payrollNumber"));
            }
            if (memberNode.has("location")) {
                member.setLocation(getJsonValue(memberNode, "location"));
            }

//            会员状态 - CRITICAL: 使用JSON驼峰格式
            member.setLastPaymentDate(getJsonValue(memberNode, "lastPaymentDate"));
            member.setMembershipTypeDesc(getJsonValue(memberNode, "membershipTypeDesc"));
            member.setEpmuMemTypeDesc(getJsonValue(memberNode, "epmuMemTypeDesc")); // CRITICAL: 用户提到的字段

//            新增完整JSON字段映射 - 使用JSON驼峰格式
            member.setAgeOfMember(getJsonValue(memberNode, "ageOfMember"));
            member.setGenderDesc(getJsonValue(memberNode, "genderDesc"));
            member.setEthnicRegionDesc(getJsonValue(memberNode, "ethnicRegionDesc"));
            member.setEthnicOriginDesc(getJsonValue(memberNode, "ethnicOriginDesc"));
            member.setSiteSubIndustryDesc(getJsonValue(memberNode, "siteSubIndustryDesc"));
            member.setBargainingGroupDesc(getJsonValue(memberNode, "bargainingGroupDesc"));

//            事件参与者特有字段 - 使用JSON驼峰格式
            member.setSitePrimOrgName(getJsonValue(memberNode, "sitePrimOrgName"));
            member.setOrgTeamPDescEpmu(getJsonValue(memberNode, "orgTeamPDescEpmu"));
            member.setDirectorName(getJsonValue(memberNode, "directorName"));
            member.setSubIndSector(getJsonValue(memberNode, "subIndSector"));

//            CRITICAL: 补充遗漏的数据库字段映射
//            兼容性字段 - 确保所有数据库字段都有对应的映射
            member.setAddress(getJsonValue(memberNode, "address")); // address字段
            member.setEmployer(getJsonValue(memberNode, "employer")); // employer字段
            member.setDepartment(getJsonValue(memberNode, "department")); // department字段

//            CRITICAL: 重要：确保telephone_mobile字段映射（用户特别强调）
//            这是Financial Form同步的关键字段
            String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");
            if (telephoneMobile != null && !telephoneMobile.trim().isEmpty()) {
                member.setTelephoneMobile(telephoneMobile.trim());
                member.setHasMobile(true);
                log.debug("Member {}: Set telephoneMobile: [{}]", membershipNumber, telephoneMobile.trim());
            } else {
                member.setHasMobile(false);
                log.debug("Member {}: No valid telephoneMobile found", membershipNumber);
            }

//            联系方式字段 - 完整映射包括所有电话类型
            member.setPhoneHome(getJsonValue(memberNode, "phoneHome")); // phone_home数据库字段
            member.setPhoneWork(getJsonValue(memberNode, "phoneWork")); // phone_work数据库字段

//            工作职位信息 - 完整映射
            member.setJobTitle(getJsonValue(memberNode, "jobTitle")); // job_title数据库字段

//            尝试从可能的备选字段名获取数据
//            如果主字段名没有数据，尝试备选字段名
            if (member.getPhoneHome() == null) {
                member.setPhoneHome(getJsonValue(memberNode, "telephoneHome"));
            }
            if (member.getPhoneWork() == null) {
                member.setPhoneWork(getJsonValue(memberNode, "telephoneWork"));
            }
            if (member.getEmployer() == null) {
                member.setEmployer(member.getEmployerName()); // 使用employerName作为backup
            }
            if (member.getJobTitle() == null) {
                member.setJobTitle(member.getOccupation()); // 使用occupation作为backup
            }
            if (member.getDepartment() == null) {
                member.setDepartment(member.getBranchDesc()); // 使用branchDesc作为backup
            }

//            设置联系方式状态标志
            member.setHasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty());
            member.setHasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty());

//            添加详细的字段映射日志用于调试
            log.debug("Member {} field mapping - Email: [{}], Mobile: [{}], WorkplaceDesc: [{}], SiteCode: [{}], KnownAs: [{}], PhoneWork: [{}], PhoneHome: [{}], EmploymentStatus: [{}], EmployerName: [{}], Location: [{}]",
                    membershipNumber,
                    member.getPrimaryEmail(),
                    member.getTelephoneMobile(),
                    member.getWorkplaceDesc(),
                    member.getSiteNumber(),
                    member.getKnownAs(),
                    member.getPhoneWork(),
                    member.getPhoneHome(),
                    member.getEmploymentStatus(),
                    member.getEmployerName(),
                    member.getLocation());

//            更新同步信息
            member.setDataSource(dataSource);
            member.setImportBatchId(batchId);
            member.setLastSyncTime(LocalDateTime.now());
            member.setSyncStatus("SUCCESS");

            if (member.getId() == null) {
                member.setCreatedAt(LocalDateTime.now());
            }

            return member;

        } catch (Exception e) {
            log.error("Failed to process member record {}: {}", recordNum, e.getMessage());
            return null;
        }
    }

    //    安全版本的processFinancialDeclarationMember，不使用嵌套事务
    private Member processFinancialDeclarationMemberSafe(JsonNode memberNode, String dataSource, String batchId, int recordNum) {
        try {
            String membershipNumber = getJsonValue(memberNode, "membershipNumber");
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Record {}: Missing membership number, skipping", recordNum);
                return null;
            }

//            检查是否已存在
            Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber);

            Member member;
            if (existingMember.isPresent()) {
                member = existingMember.get();
                log.debug("Updating existing member: {}", membershipNumber);
            } else {
                member = new Member();
                member.setMembershipNumber(membershipNumber);
//                为新成员生成必要字段避免数据库约束错误
                member.setToken(UUID.randomUUID());
                member.setVerificationCode(verificationCodeGenerator.generateSixDigitCode());
                member.setHasRegistered(false);
                member.setIsAttending(false);
                member.setIsSpecialVote(false);
                member.setHasVoted(false);
                log.debug("Creating new member: {}", membershipNumber);
            }

//            处理name字段，确保不为null - 与SMS处理方式保持一致
            String rawName = getJsonValue(memberNode, "name");
            String finalName;
            if (rawName != null && !rawName.trim().isEmpty()) {
                finalName = rawName.trim();
            } else {
//                尝试从fore1和surname组合
                String fore1 = getJsonValue(memberNode, "fore1");
                String surname = getJsonValue(memberNode, "surname");
                String composedName = ((fore1 != null ? fore1 : "") + " " + (surname != null ? surname : "")).trim();

                if (!composedName.isEmpty()) {
                    finalName = composedName;
                } else {
//                    最后备用方案：使用会员号
                    finalName = "Member " + membershipNumber;
                }
            }
            member.setName(finalName);

//            处理邮箱地址 - 修复字段名从emailAddress改为primaryEmail
            String primaryEmail = getJsonValue(memberNode, "primaryEmail"); // CRITICAL: 使用JSON驼峰格式
            if (primaryEmail != null && !primaryEmail.trim().isEmpty() && isValidEmail(primaryEmail.trim())) {
                member.setPrimaryEmail(primaryEmail.trim());
                member.setPrimaryEmail(primaryEmail.trim());
                log.debug("Member {}: Found valid primaryEmail: [{}]", membershipNumber, primaryEmail.trim());
            } else {
                member.setPrimaryEmail(null); // 允许邮箱为null
                member.setPrimaryEmail(null);
                log.debug("Member {}: No valid primaryEmail found, setting email fields to null", membershipNumber);
            }

//            处理手机号码 - CRITICAL: 重要：特殊处理telephone_mobile字段（用户强调的关键字段）
            String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");
            if (telephoneMobile != null && !telephoneMobile.trim().isEmpty()) {
                member.setTelephoneMobile(telephoneMobile.trim());
                member.setHasMobile(true);
                log.debug("Member {}: Set telephoneMobile: [{}]", membershipNumber, telephoneMobile.trim());
            } else {
                member.setHasMobile(false);
                log.debug("Member {}: No valid telephoneMobile found", membershipNumber);
            }

//            更新地址信息 - 确保包含所有地址字段
            member.setAddRes1(getJsonValue(memberNode, "addRes1")); // CRITICAL: 使用JSON驼峰格式
            member.setAddRes2(getJsonValue(memberNode, "addRes2")); // CRITICAL: 使用JSON驼峰格式
            member.setAddRes3(getJsonValue(memberNode, "addRes3")); // CRITICAL: 使用JSON驼峰格式
            member.setAddRes4(getJsonValue(memberNode, "addRes4")); // CRITICAL: 使用JSON驼峰格式
            member.setAddRes5(getJsonValue(memberNode, "addRes5")); // CRITICAL: 使用JSON驼峰格式
            member.setAddResPc(getJsonValue(memberNode, "addResPc")); // CRITICAL: 使用JSON驼峰格式

//            更新工作信息 - 完整映射（修复数组字段处理）
            member.setRegionDesc(getJsonValue(memberNode, "regionDesc"));
            member.setBranchDesc(getJsonArrayFirstValue(memberNode, "branchDesc"));      // CRITICAL: 数组字段修复
            member.setWorkplaceDesc(getJsonArrayFirstValue(memberNode, "workplaceDesc")); // CRITICAL: 数组字段修复
            member.setEmployerName(getJsonArrayFirstValue(memberNode, "employerName"));   // CRITICAL: 数组字段修复
            member.setSiteIndustryDesc(getJsonValue(memberNode, "siteIndustryDesc"));
            member.setEmploymentStatus(getJsonValue(memberNode, "employmentStatus")); // CRITICAL: 新增
            member.setSiteNumber(getJsonValue(memberNode, "siteCode")); // CRITICAL: 新增 site-code
            member.setPayrollNumber(getJsonValue(memberNode, "payrollNumber")); // CRITICAL: 新增
            member.setLocation(getJsonValue(memberNode, "location")); // CRITICAL: 新增

//            更新个人信息字段 - 完整映射
            member.setFore1(getJsonValue(memberNode, "fore1"));
            member.setSurname(getJsonValue(memberNode, "surname"));
            member.setKnownAs(getJsonValue(memberNode, "knownAs")); // CRITICAL: 使用JSON驼峰格式
            member.setFinancialIndicator(getJsonValue(memberNode, "financialIndicatorDescription"));
            member.setDob(getJsonArrayFirstValue(memberNode, "dob")); // CRITICAL: 数组字段修复：dob
            member.setEmployeeRef(getJsonValue(memberNode, "employeeRef"));
            member.setOccupation(getJsonValue(memberNode, "occupation"));

//            更新会员信息字段 - 完整映射
            member.setMembershipTypeDesc(getJsonValue(memberNode, "membershipTypeDesc"));
            member.setEpmuMemTypeDesc(getJsonValue(memberNode, "epmuMemTypeDesc"));
            member.setForumDesc(getJsonValue(memberNode, "forumDesc"));
            member.setLastPaymentDate(getJsonValue(memberNode, "lastPaymentDate"));

//            CRITICAL: 新增：缺失的重要字段映射（与processFinancialDeclarationMember保持一致）
            member.setAgeOfMember(getJsonValue(memberNode, "ageOfMember"));
            member.setGenderDesc(getJsonValue(memberNode, "genderDesc"));
            member.setEthnicRegionDesc(getJsonValue(memberNode, "ethnicRegionDesc"));
            member.setEthnicOriginDesc(getJsonValue(memberNode, "ethnicOriginDesc"));
            member.setSiteSubIndustryDesc(getJsonValue(memberNode, "siteSubIndustryDesc"));
            member.setBargainingGroupDesc(getJsonValue(memberNode, "bargainingGroupDesc"));

//            CRITICAL: 新增：事件参与者特有字段（保持方法一致性）
            member.setEthnicOriginDesc(getJsonValue(memberNode, "ethnicOriginDesc"));
            member.setSitePrimOrgName(getJsonValue(memberNode, "sitePrimOrgName"));
            member.setOrgTeamPDescEpmu(getJsonValue(memberNode, "orgTeamPDescEpmu"));
            member.setDirectorName(getJsonValue(memberNode, "directorName"));
            member.setSubIndSector(getJsonValue(memberNode, "subIndSector"));

//            CRITICAL: 补充遗漏的数据库字段映射（与processFinancialDeclarationMember保持一致）
//            兼容性字段 - 确保所有数据库字段都有对应的映射
            member.setAddress(getJsonValue(memberNode, "address")); // address字段
            member.setEmployer(getJsonValue(memberNode, "employer")); // employer字段
            member.setDepartment(getJsonValue(memberNode, "department")); // department字段

//            联系方式字段 - 完整映射包括所有电话类型
            member.setPhoneHome(getJsonValue(memberNode, "phoneHome")); // phone_home数据库字段
            member.setPhoneWork(getJsonValue(memberNode, "phoneWork")); // phone_work数据库字段

//            工作职位信息 - 完整映射
            member.setJobTitle(getJsonValue(memberNode, "jobTitle")); // job_title数据库字段

//            尝试从可能的备选字段名获取数据
//            如果主字段名没有数据，尝试备选字段名
            if (member.getPhoneHome() == null) {
                member.setPhoneHome(getJsonValue(memberNode, "telephoneHome"));
            }
            if (member.getPhoneWork() == null) {
                member.setPhoneWork(getJsonValue(memberNode, "telephoneWork"));
            }
            if (member.getEmployer() == null) {
                member.setEmployer(member.getEmployerName()); // 使用employerName作为backup
            }
            if (member.getJobTitle() == null) {
                member.setJobTitle(member.getOccupation()); // 使用occupation作为backup
            }
            if (member.getDepartment() == null) {
                member.setDepartment(member.getBranchDesc()); // 使用branchDesc作为backup
            }

//            设置联系方式状态标志
            member.setHasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty());
            member.setHasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty());

//            添加详细的字段映射日志用于调试
            log.debug("Member {} field mapping - Email: [{}], Mobile: [{}], WorkplaceDesc: [{}], SiteCode: [{}], KnownAs: [{}], PhoneWork: [{}], PhoneHome: [{}], EmploymentStatus: [{}], EmployerName: [{}], Location: [{}]",
                    membershipNumber,
                    member.getPrimaryEmail(),
                    member.getTelephoneMobile(),
                    member.getWorkplaceDesc(),
                    member.getSiteNumber(),
                    member.getKnownAs(),
                    member.getPhoneWork(),
                    member.getPhoneHome(),
                    member.getEmploymentStatus(),
                    member.getEmployerName(),
                    member.getLocation());

//            更新同步信息
            member.setDataSource(dataSource);
            member.setImportBatchId(batchId);
            member.setLastSyncTime(LocalDateTime.now());
            member.setSyncStatus("SUCCESS");

            if (member.getId() == null) {
                member.setCreatedAt(LocalDateTime.now());
            }

            return member;

        } catch (Exception e) {
            log.error("Failed to process member record {}: {}", recordNum, e.getMessage());
            return null;
        }
    }

    //    工具方法
    private String fetchDataFromUrl(String fullUrl) {
        try {
            log.debug("Fetching data from URL: {}", fullUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "ETU-Voting-System/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new RuntimeException("HTTP error: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to fetch data from URL {}: {}", fullUrl, e.getMessage());
            throw new RuntimeException("Failed to fetch data", e);
        }
    }

    private Boolean parseAttendanceStatus(String attendanceValue) {
        if (attendanceValue == null) return false;
        return "Y".equalsIgnoreCase(attendanceValue.trim()) || "W".equalsIgnoreCase(attendanceValue.trim());
    }

    private String getJsonValue(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }

        String value = fieldNode.asText();

        // CRITICAL: Clean email addresses that contain markdown links
        if ("primaryEmail".equals(fieldName) && value != null) {
            // Extract email from markdown format: [email@domain.com](mailto:email@domain.com)
            if (value.startsWith("[") && value.contains("](mailto:")) {
                int start = value.indexOf("[") + 1;
                int end = value.indexOf("](mailto:");
                if (start > 0 && end > start) {
                    value = value.substring(start, end);
                }
            }
        }

        return value;
    }

    private String getJsonArrayFirstValue(JsonNode node, String fieldName) {
        JsonNode arrayNode = node.get(fieldName);
        if (arrayNode != null && arrayNode.isArray() && arrayNode.size() > 0) {
            JsonNode firstElement = arrayNode.get(0);
            return firstElement != null && !firstElement.isNull() ? firstElement.asText() : null;
        }
        return null;
    }

    private String getJsonNestedArrayValue(JsonNode node, String fieldName) {
        JsonNode arrayNode = node.get(fieldName);
        if (arrayNode != null && arrayNode.isArray() && arrayNode.size() > 0) {
            JsonNode nestedArray = arrayNode.get(0);
            if (nestedArray != null && nestedArray.isArray() && nestedArray.size() > 0) {
                JsonNode value = nestedArray.get(0);
                return value != null && !value.isNull() ? value.asText() : null;
            }
        }
        return null;
    }

    //    同步指定事件的数据 - 更新为使用优化的直接同步方法
    @Transactional
    public void syncEventData(Event event) {
        try {
            log.info("Starting sync for event: {}", event.getName());

            // CRITICAL: 完全禁用Attendee数据同步 - BMM专用
            if (event.getInformerAttendeeUrl() != null && !event.getInformerAttendeeUrl().trim().isEmpty()) {
                log.info("⚠️ Attendee sync DISABLED for BMM focus - event: {}", event.getName());
                event.setLastAttendeeSyncTime(LocalDateTime.now());
            }

            // CRITICAL: 使用优化的直接同步方法 - 处理45000+记录
            if (event.getInformerEmailMembersUrl() != null && !event.getInformerEmailMembersUrl().trim().isEmpty()) {
                log.info("🚀 Using optimized DIRECT Email sync for event: {}", event.getName());
                syncEmailMembersDirectlyToEventMember(event.getInformerEmailMembersUrl());
                event.setLastEmailMembersSyncTime(LocalDateTime.now());
            }

            // CRITICAL: 使用优化的直接同步方法 - 处理大数据量
            if (event.getInformerSmsMembersUrl() != null && !event.getInformerSmsMembersUrl().trim().isEmpty()) {
                log.info("🚀 Using optimized DIRECT SMS sync for event: {}", event.getName());
                syncSmsMembersDirectlyToEventMember(event.getInformerSmsMembersUrl());
                event.setLastSmsMembersSyncTime(LocalDateTime.now());
            }

            event.setSyncStatus(Event.SyncStatus.SUCCESS);
            event.setLastSyncTime(LocalDateTime.now());
            eventRepository.save(event);

            log.info("✅ Event sync completed successfully using optimized methods: {}", event.getName());

        } catch (Exception e) {
            log.error("❌ Failed to sync event data for event {}: {}", event.getName(), e.getMessage());
            event.setSyncStatus(Event.SyncStatus.FAILED);
            event.setLastSyncTime(LocalDateTime.now());
            eventRepository.save(event);
            throw new RuntimeException("Event sync failed", e);
        }
    }

    //    同步所有待处理的事件
    @Transactional
    public void syncAllPendingEvents() {
        try {
            log.info("Starting sync for all pending events");

            List<Event> pendingEvents = eventRepository.findByIsActiveTrue().stream()
                    .filter(event -> event.getSyncStatus() == Event.SyncStatus.PENDING ||
                            event.getSyncStatus() == Event.SyncStatus.FAILED)
                    .collect(Collectors.toList());

            log.info("Found {} pending events to sync", pendingEvents.size());

            int successCount = 0;
            int failCount = 0;

            for (Event event : pendingEvents) {
                try {
                    syncEventData(event);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to sync event {}: {}", event.getName(), e.getMessage());
                    failCount++;
                }
            }

            log.info("Sync completed - Success: {}, Failed: {}", successCount, failCount);

        } catch (Exception e) {
            log.error("Failed to sync pending events: {}", e.getMessage());
            throw new RuntimeException("Batch sync failed", e);
        }
    }

    //    Test method to sync a small batch of data for debugging
    @Transactional
    public Map<String, Object> testSyncEmailMembers(int maxRecords) {
        log.info("Starting TEST email members data sync (max {} records)", maxRecords);
        String batchId = UUID.randomUUID().toString();
        Map<String, Object> result = new HashMap<>();

        try {
            String url = "https://etu-inf5-rsau.aptsolutions.net/api/datasets/3bdf6d2b-e642-47a5-abc8-c466b3b8910c/export/json?timezone=Pacific%2FAuckland&applyFormatting=true&token=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJrZXkiOiJjMTcxMmFiYy04MmEwLTQ0ODctYjU0ZC1mYjliZTUwYTY1OWQiLCJpYXQiOjE3NDk1OTUyMTIuMDg3fQ.tTNh6lLdBw1JOdWLInyylHwEVmgoSlGHW5mgnwDyUI5";

            String jsonData = fetchDataFromUrl(url);
            log.info("Fetched JSON data length: {}", jsonData != null ? jsonData.length() : 0);

            if (jsonData == null || jsonData.trim().isEmpty()) {
                result.put("success", false);
                result.put("error", "No data received from URL");
                return result;
            }

            List<Member> membersToSave = new ArrayList<>();
            List<String> processingErrors = new ArrayList<>();

            JsonNode rootNode = objectMapper.readTree(jsonData);
            log.info("Parsed JSON root node type: {}", rootNode.getNodeType());

            JsonNode dataArray;
            if (rootNode.isArray()) {
                dataArray = rootNode;
            } else if (rootNode.has("data") && rootNode.get("data").isArray()) {
                dataArray = rootNode.get("data");
            } else {
                result.put("success", false);
                result.put("error", "Unexpected JSON structure");
                return result;
            }

            int recordsToProcess = Math.min(maxRecords, dataArray.size());
            log.info("Processing {} of {} available records", recordsToProcess, dataArray.size());

            for (int i = 0; i < recordsToProcess; i++) {
                JsonNode memberNode = dataArray.get(i);
                try {
                    Member member = applicationContext.getBean(InformerSyncService.class).processFinancialDeclarationMember(memberNode, "INFORMER_EMAIL_TEST", batchId, i + 1);
                    if (member != null) {
                        membersToSave.add(member);
                        log.info("Processed record {}: membershipNumber={}, name={}, email={}",
                                i + 1, member.getMembershipNumber(), member.getName(), member.getPrimaryEmail());
                    } else {
                        processingErrors.add("Record " + (i + 1) + ": Failed to process (null result)");
                    }
                } catch (Exception e) {
                    String error = "Record " + (i + 1) + ": " + e.getMessage();
                    processingErrors.add(error);
                    log.error("Failed to process record {}: {}", i + 1, e.getMessage());
                }
            }

//            Save to database
            int savedCount = 0;
            if (!membersToSave.isEmpty()) {
                try {
                    List<Member> savedMembers = memberRepository.saveAll(membersToSave);
                    savedCount = savedMembers.size();
                    log.info("Successfully saved {} members to database", savedCount);
                } catch (Exception e) {
                    result.put("success", false);
                    result.put("error", "Database save failed: " + e.getMessage());
                    return result;
                }
            }

            result.put("success", true);
            result.put("totalAvailableRecords", dataArray.size());
            result.put("recordsProcessed", recordsToProcess);
            result.put("validRecords", membersToSave.size());
            result.put("savedRecords", savedCount);
            result.put("processingErrors", processingErrors);
            result.put("batchId", batchId);

            return result;

        } catch (Exception e) {
            log.error("Test sync failed: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Test sync failed: " + e.getMessage());
            return result;
        }
    }

    //    智能数据分流导入 - 根据数据类型分配到正确的表
    @Transactional
    public void smartImportFromInformerUrl(String url, Event targetEvent) {
        log.info("Starting smart import from Informer URL for event: {}", targetEvent.getName());

        try {
            String jsonData = fetchDataFromUrl(url);
            JsonNode rootNode = objectMapper.readTree(jsonData);

            if (!rootNode.isArray()) {
                throw new IllegalArgumentException("Expected JSON array from Informer");
            }

            String batchId = "SMART_IMPORT_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            int totalRecords = rootNode.size();
            int processedMembers = 0;
            int processedEventMembers = 0;
            int errors = 0;

            log.info("Processing {} records for smart import", totalRecords);

            for (int i = 0; i < totalRecords; i++) {
                JsonNode recordNode = rootNode.get(i);
                try {
//                    确定数据类型并处理
                    ImportResult result = processInformerRecord(recordNode, targetEvent, batchId, i + 1);

                    switch (result.getType()) {
                        case MEMBER:
                            processedMembers++;
                            break;
                        case EVENT_MEMBER:
                            processedEventMembers++;
                            break;
                        case ERROR:
                            errors++;
                            break;
                    }

                } catch (Exception e) {
                    log.error("Failed to process record {}: {}", i + 1, e.getMessage());
                    errors++;
                }
            }

//            更新事件统计
            targetEvent.setMemberSyncCount(processedMembers);
            targetEvent.setAttendeeSyncCount(processedEventMembers);
            targetEvent.setLastSyncTime(LocalDateTime.now());
            targetEvent.setSyncStatus(Event.SyncStatus.SUCCESS);
            eventRepository.save(targetEvent);

            log.info("Smart import completed - Members: {}, EventMembers: {}, Errors: {}",
                    processedMembers, processedEventMembers, errors);

        } catch (Exception e) {
            log.error("Smart import failed for event {}: {}", targetEvent.getName(), e.getMessage(), e);
            targetEvent.setSyncStatus(Event.SyncStatus.FAILED);
            targetEvent.setLastSyncTime(LocalDateTime.now());
            eventRepository.save(targetEvent);
            throw new RuntimeException("Smart import failed", e);
        }
    }

    //    处理单个Informer记录，智能判断数据类型
    private ImportResult processInformerRecord(JsonNode recordNode, Event event, String batchId, int recordNum) {
        try {
            String membershipNumber = getJsonValue(recordNode, "membershipNumber");

            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                log.warn("Record {}: Missing membership number, skipping", recordNum);
                return new ImportResult(ImportResult.Type.ERROR, "Missing membership number");
            }

//            Step 1: 处理Member数据 (Members表)
            Member member = findOrCreateMemberFromAttendee(recordNode, membershipNumber, batchId);

//            Step 2: 判断是否需要创建EventMember关联
            boolean shouldCreateEventMember = shouldCreateEventMemberRelation(recordNode, event);

            if (shouldCreateEventMember) {
//                Step 3: 创建EventMember关联 (Event_Members表)
                createEventMemberFromAttendee(recordNode, event, member, batchId);
                return new ImportResult(ImportResult.Type.EVENT_MEMBER, "EventMember created");
            } else {
//                只是更新Member信息
                return new ImportResult(ImportResult.Type.MEMBER, "Member updated");
            }

        } catch (Exception e) {
            log.error("Failed to process record {}: {}", recordNum, e.getMessage());
            return new ImportResult(ImportResult.Type.ERROR, e.getMessage());
        }
    }

    //    判断是否应该为此记录创建EventMember关联
    private boolean shouldCreateEventMemberRelation(JsonNode recordNode, Event event) {
//        检查记录中是否包含事件相关信息
        String eventCode = getJsonArrayFirstValue(recordNode, "etuEventCode");
        String eventType = getJsonNestedArrayValue(recordNode, "link_to_event_detail_assoc_xeventType");
        String venue = getJsonNestedArrayValue(recordNode, "link_to_event_detail_assoc_xvenue");
        String eventId = getJsonArrayFirstValue(recordNode, "event");

//        从attendee数据导入的情况：如果包含任何事件信息，都要创建EventMember关联
        if ((eventCode != null && !eventCode.trim().isEmpty()) ||
                (eventType != null && !eventType.trim().isEmpty()) ||
                (venue != null && !venue.trim().isEmpty()) ||
                (eventId != null && !eventId.trim().isEmpty())) {
            log.debug("Creating EventMember relation for attendee data - EventCode: {}, EventType: {}, Venue: {}, EventID: {}",
                    eventCode, eventType, venue, eventId);
            return true;
        }

//        对于BMM事件，所有会员都应该有EventMember关联
        if (event.getEventType() == Event.EventType.BMM_VOTING) {
            return true;
        }

//        如果事件是通过attendee数据创建的（包含datasetId标识），都要创建关联
        if (event.getDatasetId() != null && event.getDatasetId().startsWith("informer-event-")) {
            log.debug("Creating EventMember relation for informer-created event: {}", event.getName());
            return true;
        }

        return false;
    }

    //    导入结果包装类
    private static class ImportResult {
        public enum Type {
            MEMBER, EVENT_MEMBER, ERROR
        }

        private final Type type;
        private final String message;

        public ImportResult(Type type, String message) {
            this.type = type;
            this.message = message;
        }

        public Type getType() { return type; }
        public String getMessage() { return message; }
    }

    //    改进的BMM事件数据同步 - 使用特定事件而非全局事件
    @Transactional
    public void syncBMMEventData(Event bmmEvent) {
        if (!bmmEvent.getEventType().equals(Event.EventType.BMM_VOTING)) {
            throw new IllegalArgumentException("This method is only for BMM_VOTING events");
        }

        try {
            log.info("Starting BMM-specific sync for event: {}", bmmEvent.getName());

//            1. 从Email Members URL直接导入到EventMember表
            if (bmmEvent.getInformerEmailMembersUrl() != null) {
                syncEmailMembersDirectlyToEventMember(bmmEvent.getInformerEmailMembersUrl());
            }

//            2. 从SMS Members URL直接导入到EventMember表
            if (bmmEvent.getInformerSmsMembersUrl() != null) {
                syncSmsMembersDirectlyToEventMember(bmmEvent.getInformerSmsMembersUrl());
            }

//            3. 从Attendee URL导入（如果需要） - CRITICAL: 暂时注释
            // if (bmmEvent.getInformerAttendeeUrl() != null) {
            //     smartImportFromInformerUrl(bmmEvent.getInformerAttendeeUrl(), bmmEvent);
            // }

            log.info("BMM event sync completed successfully: {}", bmmEvent.getName());

        } catch (Exception e) {
            log.error("Failed to sync BMM event data for event {}: {}", bmmEvent.getName(), e.getMessage());
            bmmEvent.setSyncStatus(Event.SyncStatus.FAILED);
            bmmEvent.setLastSyncTime(LocalDateTime.now());
            eventRepository.save(bmmEvent);
            throw new RuntimeException("BMM event sync failed", e);
        }
    }

    private void autoLinkMembersToBMMEvent(List<Member> members, String batchId) {
        try {
            log.info("Starting auto-linking {} members to BMM events", members.size());

//            Find all active BMM events
            List<Event> bmmEvents = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc()
                    .stream()
                    .filter(e -> e.getEventType() == Event.EventType.BMM_VOTING)
                    .collect(Collectors.toList());

            if (bmmEvents.isEmpty()) {
                log.warn("No active BMM events found for auto-linking members");
                return;
            }

            Event bmmEvent = bmmEvents.get(0); // Use the most recent BMM event
            log.info("Auto-linking members to BMM event: {}", bmmEvent.getName());

            for (Member member : members) {
                if (member.getMembershipNumber() == null || member.getMembershipNumber().trim().isEmpty()) {
                    continue; // Skip members without membership numbers
                }

//                Check if the member is already linked to the BMM event
                Optional<EventMember> existingEventMember = eventMemberRepository.findByEventAndMembershipNumber(
                        bmmEvent, member.getMembershipNumber());

                if (existingEventMember.isPresent()) {
//                    如果EventMember已存在，更新信息但保持token不变
                    EventMember eventMember = existingEventMember.get();
                    log.debug("EventMember already exists for member {}, updating info while keeping token stable",
                            member.getMembershipNumber());

//                    更新基本信息但保持token不变
                    eventMember.setName(member.getName());
                    eventMember.setPrimaryEmail(member.getPrimaryEmail());
                    eventMember.setTelephoneMobile(member.getTelephoneMobile());
                    eventMember.setRegionDesc(member.getRegionDesc());
                    eventMember.setRegion(member.getRegionDesc());
                    eventMember.setBranch(member.getBranchDesc());
                    eventMember.setWorkplace(member.getWorkplaceDesc());
                    eventMember.setEmployer(member.getEmployerName());
                    eventMember.setImportBatchId(batchId);
                    eventMember.setUpdatedAt(LocalDateTime.now());

//                    更新状态标志
                    eventMember.setHasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty());
                    eventMember.setHasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty());

                    eventMemberRepository.save(eventMember);
                    log.debug("Updated existing EventMember for member {} while preserving token", member.getMembershipNumber());

                } else {
//                    Create new EventMember link
                    try {
                        EventMember eventMember = EventMember.builder()
                                .event(bmmEvent)
                                .member(member)
                                .membershipNumber(member.getMembershipNumber())
                                .name(member.getName())
                                .primaryEmail(member.getPrimaryEmail())
                                .telephoneMobile(member.getTelephoneMobile())
                                .token(UUID.randomUUID())
                                .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                                .hasRegistered(false)
                                .isAttending(false)
                                .isSpecialVote(false)
                                .hasVoted(false)
                                .checkedIn(false)
                                // CRITICAL: 修复：在builder中直接设置hasEmail和hasMobile字段
                                .hasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty())
                                .hasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty())
                                .regionDesc(member.getRegionDesc())
                                .region(member.getRegionDesc())
                                .branch(member.getBranchDesc())
                                .workplace(member.getWorkplaceDesc())
                                .employer(member.getEmployerName())
                                .bargainingGroup(member.getBargainingGroupDesc())
                                // CRITICAL: 新增：同步所有重要字段从Member到EventMember
                                .fore1(member.getFore1())
                                .knownAs(member.getKnownAs())
                                .surname(member.getSurname())
                                .dob(member.getDob())
                                .ageOfMember(member.getAgeOfMember())
                                .genderDesc(member.getGenderDesc())
                                .ethnicRegionDesc(member.getEthnicRegionDesc())
                                .ethnicOriginDesc(member.getEthnicOriginDesc())
                                .employmentStatus(member.getEmploymentStatus())
                                .payrollNumber(member.getPayrollNumber())
                                .siteCode(member.getSiteNumber())
                                .siteSubIndustryDesc(member.getSiteSubIndustryDesc())
                                .workplaceDesc(member.getWorkplaceDesc())
                                .sitePrimOrgName(member.getSitePrimOrgName())
                                .orgTeamPDescEpmu(member.getOrgTeamPDescEpmu())
                                .directorName(member.getDirectorName())
                                .subIndSector(member.getSubIndSector())
                                .jobTitle(member.getJobTitle())
                                .department(member.getDepartment())
                                .location(member.getLocation())
                                .phoneHome(member.getPhoneHome())
                                .phoneWork(member.getPhoneWork())
                                .address(member.getAddress())
                                .dataSource("BMM_AUTO_LINK")
                                .importBatchId(batchId)
                                .registrationStatus("PENDING")
                                .createdAt(LocalDateTime.now())
                                .build();

                        eventMemberRepository.save(eventMember);
                        log.info("Auto-linked member {} to BMM event: {} with new token",
                                member.getMembershipNumber(), bmmEvent.getName());
                    } catch (Exception e) {
                        log.error("Failed to link member {} to BMM event {}: {}",
                                member.getMembershipNumber(), bmmEvent.getName(), e.getMessage());
                    }
                }
            }

            log.info("Completed auto-linking {} members to BMM events", members.size());

        } catch (Exception e) {
            log.error("Failed to auto-link members to BMM events: {}", e.getMessage(), e);
        }
    }

    //    批量关联Members到BMM事件，避免在主事务中进行大量操作（增强版：同步更多Member信息到EventMember）
    private void autoLinkMembersToBMMEventBatch(List<Member> members, String batchId) {
        if (members.isEmpty()) {
            return;
        }

        try {
            log.info("Auto-linking {} members to BMM events (batch mode)", members.size());

//            查找活跃的BMM事件
            List<Event> bmmEvents = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc()
                    .stream()
                    .filter(e -> e.getEventType() == Event.EventType.BMM_VOTING)
                    .collect(Collectors.toList());

            if (bmmEvents.isEmpty()) {
                log.warn("No active BMM events found for auto-linking members");
                return;
            }

            Event bmmEvent = bmmEvents.get(0);
            log.info("Auto-linking members to BMM event: {}", bmmEvent.getName());

            List<EventMember> eventMembersToSave = new ArrayList<>();

            for (Member member : members) {
                if (member.getMembershipNumber() == null || member.getMembershipNumber().trim().isEmpty()) {
                    continue;
                }

//                检查是否已存在关联
                Optional<EventMember> existingEventMember = eventMemberRepository.findByEventAndMembershipNumber(
                        bmmEvent, member.getMembershipNumber());

                if (!existingEventMember.isPresent()) {
                    EventMember eventMember = EventMember.builder()
                            .event(bmmEvent)
                            .member(member)
                            .membershipNumber(member.getMembershipNumber())
                            .name(member.getName())
                            .primaryEmail(member.getPrimaryEmail())
                            .telephoneMobile(member.getTelephoneMobile())
                            .token(UUID.randomUUID())
                            .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                            .hasRegistered(false)
                            .isAttending(false)
                            .isSpecialVote(false)
                            .hasVoted(false)
                            .checkedIn(false)
                            // CRITICAL: 修复：在builder中直接设置hasEmail和hasMobile字段
                            .hasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty())
                            .hasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty())
                            .regionDesc(member.getRegionDesc())
                            .region(member.getRegionDesc())
                            .branch(member.getBranchDesc())
                            .workplace(member.getWorkplaceDesc())
                            .employer(member.getEmployerName())
                            .bargainingGroup(member.getBargainingGroupDesc())
                            // CRITICAL: 新增：重要的分类字段同步
                            .siteIndustryDesc(member.getSiteIndustryDesc())
                            .membershipTypeDesc(member.getMembershipTypeDesc())
                            .bargainingGroupDesc(member.getBargainingGroupDesc())
                            .siteSubIndustryDesc(member.getSiteSubIndustryDesc())
                            .genderDesc(member.getGenderDesc())
                            .ageOfMember(member.getAgeOfMember())
                            .ethnicRegionDesc(member.getEthnicRegionDesc())
                            .employmentStatus(member.getEmploymentStatus())
                            .dataSource("BMM_AUTO_LINK")
                            .importBatchId(batchId)
                            .registrationStatus("PENDING")
                            .createdAt(LocalDateTime.now())
                            .build();

                    eventMembersToSave.add(eventMember);
                }
            }

//            批量保存EventMember关联
            if (!eventMembersToSave.isEmpty()) {
                eventMemberRepository.saveAll(eventMembersToSave);
                log.info("Auto-linked {} new members to BMM event: {}", eventMembersToSave.size(), bmmEvent.getName());
            } else {
                log.info("No new EventMember relationships to create for this batch");
            }

        } catch (Exception e) {
            log.error("Failed to auto-link members to BMM events: {}", e.getMessage(), e);
//            不抛出异常，避免影响主导入流程
        }
    }

    //    安全版本的批量关联Members到BMM事件，用于独立事务中调用
    private void autoLinkMembersToBMMEventBatchSafe(List<Member> members, String batchId) {
        if (members.isEmpty()) {
            return;
        }

//        在独立事务中执行EventMember关联操作
        try {
            applicationContext.getBean(InformerSyncService.class)
                    .createEventMemberLinksInNewTransaction(members, batchId);
        } catch (Exception e) {
            log.error("Failed to auto-link members to BMM events in safe mode: {}", e.getMessage());
//            不抛出异常，避免影响主导入流程
        }
    }

    //    在新事务中创建EventMember关联（增强版：同步更多Member信息到EventMember）
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createEventMemberLinksInNewTransaction(List<Member> members, String batchId) {
        try {
            log.info("Creating EventMember links for {} members in new transaction", members.size());

//            查找活跃的BMM事件
            List<Event> bmmEvents = eventRepository.findTop20ByIsActiveTrueOrderByEventDateDesc()
                    .stream()
                    .filter(e -> e.getEventType() == Event.EventType.BMM_VOTING)
                    .collect(Collectors.toList());

            if (bmmEvents.isEmpty()) {
                log.warn("No active BMM events found for auto-linking members");
                return;
            }

            Event bmmEvent = bmmEvents.get(0);
            log.info("Creating links to BMM event: {}", bmmEvent.getName());

            List<EventMember> eventMembersToSave = new ArrayList<>();

            for (Member member : members) {
                if (member.getMembershipNumber() == null || member.getMembershipNumber().trim().isEmpty()) {
                    continue;
                }

//                检查是否已存在关联
                Optional<EventMember> existingEventMember = eventMemberRepository.findByEventAndMembershipNumber(
                        bmmEvent, member.getMembershipNumber());

                if (!existingEventMember.isPresent()) {
                    EventMember eventMember = EventMember.builder()
                            .event(bmmEvent)
                            .member(member)
                            .membershipNumber(member.getMembershipNumber())
                            .name(member.getName())
                            .primaryEmail(member.getPrimaryEmail())
                            .telephoneMobile(member.getTelephoneMobile())
                            .token(UUID.randomUUID())
                            .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                            .hasRegistered(false)
                            .isAttending(false)
                            .isSpecialVote(false)
                            .hasVoted(false)
                            .checkedIn(false)
                            // CRITICAL: 修复：在builder中直接设置hasEmail和hasMobile字段
                            .hasEmail(member.getPrimaryEmail() != null && !member.getPrimaryEmail().trim().isEmpty())
                            .hasMobile(member.getTelephoneMobile() != null && !member.getTelephoneMobile().trim().isEmpty())
                            .regionDesc(member.getRegionDesc())
                            .region(member.getRegionDesc())
                            .branch(member.getBranchDesc())
                            .workplace(member.getWorkplaceDesc())
                            .employer(member.getEmployerName())
                            .bargainingGroup(member.getBargainingGroupDesc()) // CRITICAL: 从Member同步更多信息
                            .dataSource("BMM_AUTO_LINK")
                            .importBatchId(batchId)
                            .registrationStatus("PENDING")
                            .createdAt(LocalDateTime.now())
                            .build();

                    eventMembersToSave.add(eventMember);
                }
            }

//            批量保存EventMember关联
            if (!eventMembersToSave.isEmpty()) {
                eventMemberRepository.saveAll(eventMembersToSave);
                log.info("Successfully created {} EventMember links in new transaction", eventMembersToSave.size());
            } else {
                log.info("No new EventMember relationships to create in this transaction");
            }

        } catch (Exception e) {
            log.error("Failed to create EventMember links in new transaction: {}", e.getMessage());
            throw e; // 在独立事务中可以抛出异常
        }
    }

    //    Validate email format
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    // CRITICAL: Create default BMM event method - added transaction annotation
    @Transactional
    protected Event createDefaultBMMEvent() {
        try {
            log.info("Creating default BMM event for data sync...");

            // CRITICAL: Check if default BMM event already exists first
            Optional<Event> existingEvent = eventRepository.findByEventCode("BMM_2024_AUTO");
            if (existingEvent.isPresent()) {
                log.info("Default BMM event already exists: {} (ID: {})", existingEvent.get().getName(), existingEvent.get().getId());
                return existingEvent.get();
            }

            Event bmmEvent = Event.builder()
                    .name("BMM 2024 - Auto Created")
                    .eventCode("BMM_2024_AUTO")
                    .datasetId("auto-created-bmm-2024")
                    .description("Automatically created BMM event for data synchronization")
                    .eventType(Event.EventType.BMM_VOTING)
                    .eventDate(LocalDateTime.now().plusMonths(1)) // Set to one month later
                    .venue("TBA")
                    .isActive(true)
                    .isVotingEnabled(true)
                    .registrationOpen(true)
                    .qrScanEnabled(true)
                    .syncStatus(Event.SyncStatus.SUCCESS)
                    .memberSyncCount(0)
                    .attendeeSyncCount(0)
                    .autoSyncEnabled(false)
                    .build();

            Event saved = eventRepository.save(bmmEvent);
            log.info("Created default BMM event: {} (ID: {})", saved.getName(), saved.getId());
            return saved;

        } catch (Exception e) {
            log.error("Failed to create default BMM event: {}", e.getMessage());
            throw new RuntimeException("Cannot create default BMM event", e);
        }
    }

    // Get active BMM event - added read-only transaction to avoid connection leaks
    @Transactional(readOnly = true)
    protected Event getBMMEvent() {
        return eventRepository.findByEventType(Event.EventType.BMM_VOTING)
                .stream()
                .filter(Event::getIsActive)
                .findFirst()
                .orElse(null);
    }
}