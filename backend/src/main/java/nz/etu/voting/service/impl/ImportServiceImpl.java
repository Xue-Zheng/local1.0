package nz.etu.voting.service.impl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.dto.response.ImportResponse;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.service.ImportService;
import nz.etu.voting.util.VerificationCodeGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {
    private final MemberRepository memberRepository;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${informer.base.url:https://etu-inf5-rsau.aptsolutions.net}")
    private String informerBaseUrl;

    @Override
    @Transactional
    public ImportResponse importFromToken(String tokenInput) {
        log.info("Starting token import");

        try {
            String fullUrl = buildFullUrl(tokenInput);
            log.info("Request URL: {}", fullUrl);

            String jsonData = fetchDataFromInformer(fullUrl);

            return processJsonData(jsonData);

        } catch (Exception e) {
            log.error("Token import failed: {}", e.getMessage(), e);
            return ImportResponse.builder()
                    .total(0)
                    .success(0)
                    .failed(1)
                    .errors(Arrays.asList("Import failed: " + e.getMessage()))
                    .build();
        }
    }

    private String buildFullUrl(String tokenInput) {
        if (tokenInput.startsWith("http")) {
            return tokenInput;
        } else {
            return informerBaseUrl + "/api/datasets/" + tokenInput;
        }
    }

    private String fetchDataFromInformer(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            headers.set("User-Agent", "ETU-Voting-System/1.0");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("HTTP request failed, status: " + response.getStatusCode());
            }

            String responseBody = response.getBody();
            if (StringUtils.isBlank(responseBody)) {
                throw new RuntimeException("Empty response body");
            }

            log.info("Successfully fetched data, length: {}", responseBody.length());
            return responseBody;

        } catch (Exception e) {
            log.error("Failed to fetch data from Informer: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch data: " + e.getMessage(), e);
        }
    }

    private ImportResponse processJsonData(String jsonData) {
        List<String> errors = new ArrayList<>();
        List<Member> membersToSave = new ArrayList<>();
        int totalRecords = 0;
        int successCount = 0;
        int failureCount = 0;

        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            JsonNode dataArray = rootNode;

            if (rootNode.has("data")) {
                dataArray = rootNode.get("data");
            }

            if (!dataArray.isArray()) {
                throw new IllegalArgumentException("Response data is not an array");
            }

            totalRecords = dataArray.size();
            log.info("Processing {} records", totalRecords);

            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode memberNode = dataArray.get(i);
                try {
                    // Process all members using the same logic
                    Member member = processRegularMemberJson(memberNode, i + 1);

                    membersToSave.add(member);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.add("Record " + (i + 1) + ": " + e.getMessage());
                    log.error("Failed to process record {}: {}", i + 1, e.getMessage());
                }
            }

            if (!membersToSave.isEmpty()) {
                log.info("Saving {} members to database", membersToSave.size());
                memberRepository.saveAll(membersToSave);
            }

        } catch (Exception e) {
            log.error("JSON data processing failed: {}", e.getMessage(), e);
            return ImportResponse.builder()
                    .total(0)
                    .success(0)
                    .failed(1)
                    .errors(Arrays.asList("Data processing failed: " + e.getMessage()))
                    .build();
        }

        return ImportResponse.builder()
                .total(totalRecords)
                .success(successCount)
                .failed(failureCount)
                .errors(errors)
                .build();
    }

    private Member processRegularMemberJson(JsonNode memberNode, int recordNum) {

        String name = getJsonValue(memberNode, "name", "Name", "Full Name");
        String email = getJsonValue(memberNode, "email", "Email", "Primary Email");
        String membershipNumber = getJsonValue(memberNode, "membership_number", "Membership Number", "Member Number");

        if (StringUtils.isBlank(name)) {
            String firstName = getJsonValue(memberNode, "First Name", "first_name");
            String surname = getJsonValue(memberNode, "Surname", "surname", "last_name");
            if (StringUtils.isNotBlank(firstName) || StringUtils.isNotBlank(surname)) {
                name = (StringUtils.defaultString(firstName) + " " + StringUtils.defaultString(surname)).trim();
            }
        }

        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Name is required");
        }
        if (StringUtils.isBlank(email) || !isValidEmail(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (StringUtils.isBlank(membershipNumber)) {
            throw new IllegalArgumentException("Membership number is required");
        }

//        检查会员号是否已存在（会员号必须唯一）
        if (memberRepository.findByMembershipNumber(membershipNumber.trim()).isPresent()) {
            throw new IllegalArgumentException("Membership number already exists: " + membershipNumber);
        }

        Member member = Member.builder()
                .name(name.trim())
                .primaryEmail(email.trim())
                .membershipNumber(membershipNumber.trim())
                .token(UUID.randomUUID())
                .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                .hasRegistered(false)
                .isAttending(false)
                .isSpecialVote(false)
                .hasVoted(false)
                // isSpecialMember field removed
                .build();

        setOptionalJsonField(memberNode, "address", "Address", member::setAddress);
        setOptionalJsonField(memberNode, "phone_mobile", "Mobile", "Phone Mobile", member::setTelephoneMobile);
        setOptionalJsonField(memberNode, "employer", "Employer", "Company", member::setEmployer);
        setOptionalJsonField(memberNode, "job_title", "Job Title", "Occupation", member::setJobTitle);

        return member;
    }

    private Member processSpecialMemberJson(JsonNode memberNode, int recordNum) {
        String memberNumber = getJsonValue(memberNode, "Member Number", "member_number");
        String email = getJsonValue(memberNode, "Link to Member Primary Email", "email", "Email");
        String firstName = getJsonValue(memberNode, "Link to Member Forename1", "first_name", "First Name");
        String lastName = getJsonValue(memberNode, "Link to Member Surname", "last_name", "Surname");

        String fullName = (StringUtils.defaultString(firstName) + " " + StringUtils.defaultString(lastName)).trim();

        if (StringUtils.isBlank(memberNumber)) {
            throw new IllegalArgumentException("Member Number is required");
        }
        if (StringUtils.isBlank(email) || !isValidEmail(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (StringUtils.isBlank(fullName)) {
            throw new IllegalArgumentException("Name is required");
        }

        Optional<Member> existingMember = memberRepository.findByMembershipNumber(memberNumber.trim());

        if (existingMember.isPresent()) {

            Member member = existingMember.get();
            log.debug("Found existing member: {}", member.getName());

            member.setName(fullName);
            member.setPrimaryEmail(email.trim());
            member.setToken(UUID.randomUUID());
            member.setVerificationCode(verificationCodeGenerator.generateSixDigitCode());
            // isSpecialMember field removed

            setOptionalJsonField(memberNode, "Link to Member Telephone Mobile", member::setTelephoneMobile);
            setOptionalJsonField(memberNode, "Link to Member Employer Name", member::setEmployer);

            return member;
        } else {

            return Member.builder()
                    .name(fullName)
                    .primaryEmail(email.trim())
                    .membershipNumber(memberNumber.trim())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    // isSpecialMember field removed
                    .hasVoted(false)
                    .build();
        }
    }

    private String getJsonValue(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                String value = node.get(fieldName).asText();
                if (StringUtils.isNotBlank(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private void setOptionalJsonField(JsonNode node, String fieldName, java.util.function.Consumer<String> setter) {
        String value = getJsonValue(node, fieldName);
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private void setOptionalJsonField(JsonNode node, String fieldName1, String fieldName2, java.util.function.Consumer<String> setter) {
        String value = getJsonValue(node, fieldName1, fieldName2);
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private void setOptionalJsonField(JsonNode node, String fieldName1, String fieldName2, String fieldName3, java.util.function.Consumer<String> setter) {
        String value = getJsonValue(node, fieldName1, fieldName2, fieldName3);
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    @Override
    @Transactional
    public ImportResponse importMembersFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        List<String> errors = new ArrayList<>();
        List<Member> membersToSave = new ArrayList<>();
        int totalRecords = 0;
        int successCount = 0;
        int failureCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {

            String[] headers = csvReader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty or invalid");
            }

            log.info("CSV headers for member import: {}", Arrays.toString(headers));

            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim(), i);
            }

//            check use Financial Declaration format or not
            boolean isFinancialDeclarationFormat = headerMap.containsKey("Membership Number") ||
                    headerMap.containsKey("First Name") ||
                    headerMap.containsKey("Surname") ||
                    headerMap.containsKey("Financial Description");

            log.info("Detected format: {}", isFinancialDeclarationFormat ? "Financial Declaration" : "Standard");

//            base on format to certificate
            if (isFinancialDeclarationFormat) {
                if (!headerMap.containsKey("Membership Number")) {
                    throw new IllegalArgumentException("CSV must contain required column: Membership Number");
                }
            } else {
                if (!headerMap.containsKey("name") || !headerMap.containsKey("primaryEmail") || !headerMap.containsKey("membership_number")) {
                    throw new IllegalArgumentException("CSV must contain at least name, email, and membership_number columns");
                }
            }

            String[] line;
            int rowNum = 1;
            while ((line = csvReader.readNext()) != null) {
                rowNum++;
                totalRecords++;
                try {
                    Member member;
                    if (isFinancialDeclarationFormat) {
                        member = processFinancialDeclarationRow(line, headerMap, rowNum);
                    } else {
                        member = processRow(line, headerMap, rowNum);
                    }

                    membersToSave.add(member);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                    log.error("Error processing row {}: {}", rowNum, e.getMessage());
                }
            }

            if (!membersToSave.isEmpty()) {
                log.info("Saving {} members to database", membersToSave.size());
                memberRepository.saveAll(membersToSave);
            }
        } catch (IOException | CsvValidationException e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error reading CSV file: " + e.getMessage(), e);
        }

        return ImportResponse.builder()
                .total(totalRecords)
                .success(successCount)
                .failed(failureCount)
                .errors(errors)
                .build();
    }

    @Override
    @Transactional
    public ImportResponse importMembersFromCsvWithDetails(MultipartFile file) {
        log.info("Starting CSV member import");
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        List<String> errors = new ArrayList<>();
        List<Member> membersToSave = new ArrayList<>();
        int totalRecords = 0;
        int successCount = 0;
        int failureCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {

            String[] headers = csvReader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty or invalid");
            }

            log.info("CSV headers: {}", Arrays.toString(headers));

            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim(), i);
            }

            if (!headerMap.containsKey("Member Number") ||
                    !headerMap.containsKey("Link to Member Primary Email") ||
                    !headerMap.containsKey("Link to Member Forename1") ||
                    !headerMap.containsKey("Link to Member Surname")) {
                throw new IllegalArgumentException("CSV must contain required columns: Member Number, Link to Member Primary Email, Link to Member Forename1, Link to Member Surname");
            }

            String[] line;
            int rowNum = 1;
            while ((line = csvReader.readNext()) != null) {
                rowNum++;
                totalRecords++;
                try {
                    Member member = processSpecialMemberRow(line, headerMap, rowNum);
                    membersToSave.add(member);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.add("Row " + rowNum + ": " + e.getMessage());
                    log.error("Error processing row {}: {}", rowNum, e.getMessage());
                }
            }

            log.info("Processed {} records, success: {}, failed: {}", totalRecords, successCount, failureCount);

            if (!membersToSave.isEmpty()) {
                log.info("Saving {} members to database", membersToSave.size());
                memberRepository.saveAll(membersToSave);
            }
        } catch (IOException | CsvValidationException e) {
            log.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error reading CSV file: " + e.getMessage(), e);
        }

        return ImportResponse.builder()
                .total(totalRecords)
                .success(successCount)
                .failed(failureCount)
                .errors(errors)
                .build();
    }

    private Member processRow(String[] row, Map<String, Integer> headerMap, int rowNum) {
        String name = getValueByHeader(row, headerMap, "name");
        String email = getValueByHeader(row, headerMap, "email");
        String membershipNumber = getValueByHeader(row, headerMap, "membership_number");

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (email == null || email.trim().isEmpty() || !isValidEmail(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Membership number is required");
        }

//        检查是否已存在会员（优先按会员号查找）
        Optional<Member> existingMemberByNumber = memberRepository.findByMembershipNumber(membershipNumber.trim());

        if (existingMemberByNumber.isPresent()) {
//            更新现有会员信息
            Member existingMember = existingMemberByNumber.get();
            log.debug("Updating existing member: {} ({})", existingMember.getName(), membershipNumber);

            existingMember.setName(name.trim());
            existingMember.setPrimaryEmail(email.trim());
            // Keep existing token and verification code stable
            existingMember.setDataSource("CSV_UPDATED");

//            更新可选字段
            updateOptionalFields(existingMember, row, headerMap);

            return existingMember;
        }

//注意：邮箱可以重复，只有会员号需要唯一

//        创建新会员
        Member member = Member.builder()
                .name(name.trim())
                .primaryEmail(email.trim())
                .membershipNumber(membershipNumber.trim())
                .token(UUID.randomUUID())
                .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                .hasRegistered(false)
                .isAttending(false)
                .isSpecialVote(false)
                .hasVoted(false)
                // isSpecialMember field removed
                .dataSource("CSV_IMPORT")
                .build();

//        更新可选字段
        updateOptionalFields(member, row, headerMap);

        return member;
    }

    private void updateOptionalFields(Member member, String[] row, Map<String, Integer> headerMap) {
        if (headerMap.containsKey("dob")) {
            String dobString = getValueByHeader(row, headerMap, "dob");
            if (dobString != null && !dobString.trim().isEmpty()) {
                try {
                    LocalDate dob = LocalDate.parse(dobString.trim(), DateTimeFormatter.ISO_DATE);
                    member.setDobLegacy(dob); // 使用Legacy字段存储LocalDate
                    member.setDob(dobString.trim()); // 保持原String格式
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid date format for dob. Use YYYY-MM-DD format");
                }
            }
        }

        setOptionalField(row, headerMap, "address", member::setAddress);
        setOptionalField(row, headerMap, "phone_home", member::setPhoneHome);
        setOptionalField(row, headerMap, "phone_mobile", member::setTelephoneMobile);
        setOptionalField(row, headerMap, "phone_work", member::setPhoneWork);
        setOptionalField(row, headerMap, "employer", member::setEmployer);
        setOptionalField(row, headerMap, "payroll_number", member::setPayrollNumber);
        setOptionalField(row, headerMap, "site_number", member::setSiteNumber);
        setOptionalField(row, headerMap, "employment_status", member::setEmploymentStatus);
        setOptionalField(row, headerMap, "department", member::setDepartment);
        setOptionalField(row, headerMap, "job_title", member::setJobTitle);
        setOptionalField(row, headerMap, "location", member::setLocation);
    }

    private Member processSpecialMemberRow(String[] row, Map<String, Integer> headerMap, int rowNum) {
        try {
            log.debug("Processing row {}", rowNum);

            String memberNumber = getValueByHeader(row, headerMap, "Member Number");
            String email = getValueByHeader(row, headerMap, "Link to Member Primary Email");
            String firstName = getValueByHeader(row, headerMap, "Link to Member Forename1");
            String lastName = getValueByHeader(row, headerMap, "Link to Member Surname");

            String firstNameTrimmed = firstName != null ? firstName.trim() : "";
            String lastNameTrimmed = lastName != null ? lastName.trim() : "";
            String fullName = (firstNameTrimmed + " " + lastNameTrimmed).trim();

            if (memberNumber == null || memberNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Member Number is required");
            }
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email is required");
            }
            if (fullName.isEmpty()) {
                throw new IllegalArgumentException("At least first name or last name must be provided");
            }

            if (email != null && !email.trim().isEmpty() && !isValidEmail(email.trim())) {
                throw new IllegalArgumentException("Invalid email format: " + email);
            }

            String phoneMobile = getValueByHeader(row, headerMap, "Link to Member Telephone Mobile");
            String location = getValueByHeader(row, headerMap, "Link to Member Office Name");
            String siteCode = getValueByHeader(row, headerMap, "Site Code");
            String employer = getValueByHeader(row, headerMap, "Link to Member Employer Name");
            String department = getValueByHeader(row, headerMap, "Link to Member Site Industry Desc");
            String jobTitle = getValueByHeader(row, headerMap, "Link to Member Site Sub Industry Desc");

            Optional<Member> existingMemberByNumber = memberRepository.findByMembershipNumber(memberNumber.trim());

            if (existingMemberByNumber.isPresent()) {
                Member existingMember = existingMemberByNumber.get();
                log.debug("Found existing member: {}", existingMember.getName());

                existingMember.setName(fullName);
                existingMember.setPrimaryEmail(email.trim());
//                Keep existing token and verification code stable
                existingMember.setIsSpecialVote(false);
                // isSpecialMember field removed

                if (phoneMobile != null && !phoneMobile.trim().isEmpty()) {
                    existingMember.setTelephoneMobile(phoneMobile.trim());
                }
                if (location != null && !location.trim().isEmpty()) {
                    existingMember.setLocation(location.trim());
                }
                if (siteCode != null && !siteCode.trim().isEmpty()) {
                    existingMember.setLocation(siteCode.trim());
                    log.debug("Found site code: {}", existingMember.getName(), siteCode);
                }
                if (employer != null && !employer.trim().isEmpty()) {
                    existingMember.setEmployer(employer.trim());
                }
                if (department != null && !department.trim().isEmpty()) {
                    existingMember.setDepartment(department.trim());
                }
                if (jobTitle != null && !jobTitle.trim().isEmpty()) {
                    existingMember.setJobTitle(jobTitle.trim());
                }

                return existingMember;
            } else {
                UUID newToken = UUID.randomUUID();
                String verificationCode = verificationCodeGenerator.generateSixDigitCode();

                Member newMember = Member.builder()
                        .name(fullName)
                        .primaryEmail(email.trim())
                        .membershipNumber(memberNumber.trim())
                        .token(newToken)
                        .verificationCode(verificationCode)
                        .hasRegistered(false)
                        .isAttending(false)
                        .isSpecialVote(false)
                        // isSpecialMember field removed
                        .hasVoted(false)
                        .build();

                if (phoneMobile != null && !phoneMobile.trim().isEmpty()) {
                    newMember.setTelephoneMobile(phoneMobile.trim());
                }
                if (location != null && !location.trim().isEmpty()) {
                    newMember.setLocation(location.trim());
                }
                if (employer != null && !employer.trim().isEmpty()) {
                    newMember.setEmployer(employer.trim());
                }
                if (department != null && !department.trim().isEmpty()) {
                    newMember.setDepartment(department.trim());
                }
                if (jobTitle != null && !jobTitle.trim().isEmpty()) {
                    newMember.setJobTitle(jobTitle.trim());
                }
                if (siteCode != null && !siteCode.trim().isEmpty()) {
                    newMember.setSiteNumber(siteCode.trim());
                    log.debug("For new member set site number: {}", newMember.getName(), siteCode);
                }

                return newMember;
            }
        } catch (Exception e) {
            log.error("Error processing row {}: {}", rowNum, e.getMessage());
            throw new RuntimeException("Error processing row " + rowNum + ": " + e.getMessage(), e);
        }
    }

    //    deal with Financial Declaration format
    private Member processFinancialDeclarationRow(String[] row, Map<String, Integer> headerMap, int rowNum) {
        try {
            log.debug("Processing Financial Declaration row {}", rowNum);

//            abstract basic field
            String membershipNumber = getValueByHeader(row, headerMap, "Membership Number");
            String firstName = getValueByHeader(row, headerMap, "First Name");
            String knownAs = getValueByHeader(row, headerMap, "Known As");
            String surname = getValueByHeader(row, headerMap, "Surname");
            String email = getValueByHeader(row, headerMap, "Email");
            String mobile = getValueByHeader(row, headerMap, "Mobile");

//            display name - use Known As for priority
            String displayFirstName = (knownAs != null && !knownAs.trim().isEmpty()) ? knownAs.trim() :
                    (firstName != null ? firstName.trim() : "");
            String surnameTrimmed = surname != null ? surname.trim() : "";
            String fullName = (displayFirstName + " " + surnameTrimmed).trim();

//            required field
            if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Membership Number is required");
            }
            if (fullName.isEmpty()) {
                throw new IllegalArgumentException("Name information is required");
            }

//            gain date field
            String dobString = getValueByHeader(row, headerMap, "Date Of Birth");
            LocalDate dob = null;
            if (dobString != null && !dobString.trim().isEmpty()) {
                try {
                    try {
//                        dd/MM/yyyy
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                        dob = LocalDate.parse(dobString.trim(), formatter);
                    } catch (DateTimeParseException e) {
//                        yyyy-MM-dd
                        DateTimeFormatter altFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        dob = LocalDate.parse(dobString.trim(), altFormatter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse date: {}", dobString);
                }
            }

//            combine address field
            StringBuilder addressBuilder = new StringBuilder();
            String addressLine1 = getValueByHeader(row, headerMap, "Address line 1");
            String addressLine2 = getValueByHeader(row, headerMap, "Address line 2 (if required)");
            String addressLine3 = getValueByHeader(row, headerMap, "Address line 3 (if required)");
            String suburb = getValueByHeader(row, headerMap, "Suburb");
            String city = getValueByHeader(row, headerMap, "City / Town");
            String postCode = getValueByHeader(row, headerMap, "Address Post Code");

            if (addressLine1 != null && !addressLine1.trim().isEmpty()) {
                addressBuilder.append(addressLine1.trim());
            }
            if (addressLine2 != null && !addressLine2.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(addressLine2.trim());
            }
            if (addressLine3 != null && !addressLine3.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(addressLine3.trim());
            }
            if (suburb != null && !suburb.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(suburb.trim());
            }
            if (city != null && !city.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(", ");
                addressBuilder.append(city.trim());
            }
            if (postCode != null && !postCode.trim().isEmpty()) {
                if (addressBuilder.length() > 0) addressBuilder.append(" ");
                addressBuilder.append(postCode.trim());
            }
            String fullAddress = addressBuilder.toString();

//            work information
            String employer = getValueByHeader(row, headerMap, "Employer / Company");
            String worksite = getValueByHeader(row, headerMap, "Worksite");
            String occupation = getValueByHeader(row, headerMap, "Occupation");
            String region = getValueByHeader(row, headerMap, "Region");
            String officeName = getValueByHeader(row, headerMap, "Office Name");
            String forumName = getValueByHeader(row, headerMap, "Forum Name");
            String payrollNumber = getValueByHeader(row, headerMap, "Payroll Number (if known)");

//            if exist member
            Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber.trim());

            if (existingMember.isPresent()) {
//                update member
                Member member = existingMember.get();
                member.setName(fullName);

//                only update when email not empty
                if (email != null && !email.trim().isEmpty()) {
                    member.setPrimaryEmail(email.trim());
                }

//                Keep existing token and verification code stable
                // isSpecialMember field removed


                if (dob != null) {
                    member.setDobLegacy(dob);
                    member.setDob(dobString);
                }
                if (mobile != null && !mobile.trim().isEmpty()) member.setTelephoneMobile(mobile.trim());
                if (fullAddress != null && !fullAddress.isEmpty()) member.setAddress(fullAddress);
                if (employer != null && !employer.trim().isEmpty()) member.setEmployer(employer.trim());
                if (worksite != null && !worksite.trim().isEmpty()) member.setLocation(worksite.trim());
                if (occupation != null && !occupation.trim().isEmpty()) member.setJobTitle(occupation.trim());
                if (payrollNumber != null && !payrollNumber.trim().isEmpty()) member.setPayrollNumber(payrollNumber.trim());
                if (region != null && !region.trim().isEmpty()) member.setDepartment(region.trim());

                return member;
            } else {
                Member member = Member.builder()
                        .name(fullName)
                        .membershipNumber(membershipNumber.trim())
                        .token(UUID.randomUUID())
                        .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                        .hasRegistered(false)
                        .isAttending(false)
                        .isSpecialVote(false)
                        // isSpecialMember field removed
                        .hasVoted(false)
                        .build();

                if (email != null && !email.trim().isEmpty()) {
                    member.setPrimaryEmail(email.trim());
                } else if (mobile != null && !mobile.trim().isEmpty()) {

                    member.setPrimaryEmail(mobile.trim().replaceAll("[^0-9]", "") + "@temp-email.etu.nz");
                } else {

                    member.setPrimaryEmail("member-" + membershipNumber.trim() + "@temp-email.etu.nz");
                }

                if (dob != null) {
                    member.setDobLegacy(dob);
                    member.setDob(dobString);
                }
                if (mobile != null && !mobile.trim().isEmpty()) member.setTelephoneMobile(mobile.trim());
                if (fullAddress != null && !fullAddress.isEmpty()) member.setAddress(fullAddress);
                if (employer != null && !employer.trim().isEmpty()) member.setEmployer(employer.trim());
                if (worksite != null && !worksite.trim().isEmpty()) member.setLocation(worksite.trim());
                if (occupation != null && !occupation.trim().isEmpty()) member.setJobTitle(occupation.trim());
                if (payrollNumber != null && !payrollNumber.trim().isEmpty()) member.setPayrollNumber(payrollNumber.trim());
                if (region != null && !region.trim().isEmpty()) member.setDepartment(region.trim());

                return member;
            }
        } catch (Exception e) {
            log.error("Error processing row {}: {}", rowNum, e.getMessage());
            throw new RuntimeException("Error processing row " + rowNum + ": " + e.getMessage(), e);
        }
    }

    private String getValueByHeader(String[] row, Map<String, Integer> headerMap, String headerName) {
        Integer index = headerMap.get(headerName);
        if (index != null) {
            for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                if (entry.getKey() .equalsIgnoreCase(headerName)) {
                    index = entry.getValue();
                    break;
                }
            }
        }
        if (index != null && index < row.length) {
            return row[index];
        }
        return null;
    }

    private void setOptionalField(String[] row, Map<String, Integer> headerMap, String headerName, java.util.function.Consumer<String> setter) {
        String value = getValueByHeader(row, headerMap, headerName);
        if (value != null && !value.trim().isEmpty()) {
            setter.accept(value.trim());
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    //    新增：数据源优先级管理
    private String determineDataSourcePriority(String newDataSource, String existingDataSource) {
//        定义数据源优先级 (数字越小优先级越高)
        Map<String, Integer> priorityMap = Map.of(
                "INFORMER_EMAIL_MEMBERS", 1, // 最高优先级
                "INFORMER_SMS_MEMBERS", 2,
                "INFORMER_AUTO_CREATED", 3,
                "CSV_EMERGENCY", 4, // 应急导入
                "CSV_UPDATED", 5,
                "CSV_IMPORT", 6,
                "MANUAL", 7 // 最低优先级
        );

        int newPriority = priorityMap.getOrDefault(newDataSource, 999);
        int existingPriority = priorityMap.getOrDefault(existingDataSource, 999);

//        返回优先级更高的数据源
        return newPriority <= existingPriority ? newDataSource : existingDataSource;
    }

    //    修改processRow方法，支持应急模式
    private Member processRowWithPriority(String[] row, Map<String, Integer> headerMap, int rowNum, boolean isEmergencyMode) {
        String name = getValueByHeader(row, headerMap, "name");
        String email = getValueByHeader(row, headerMap, "email");
        String membershipNumber = getValueByHeader(row, headerMap, "membership_number");

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (email == null || email.trim().isEmpty() || !isValidEmail(email)) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (membershipNumber == null || membershipNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Membership number is required");
        }

//        确定数据源类型
        String newDataSource = isEmergencyMode ? "CSV_EMERGENCY" : "CSV_IMPORT";

//        检查是否已存在会员（优先按会员号查找）
        Optional<Member> existingMemberByNumber = memberRepository.findByMembershipNumber(membershipNumber.trim());

        if (existingMemberByNumber.isPresent()) {
            Member existingMember = existingMemberByNumber.get();
            String existingDataSource = existingMember.getDataSource();

//            检查数据源优先级
            String finalDataSource = determineDataSourcePriority(newDataSource, existingDataSource);

            if (finalDataSource.equals(newDataSource)) {
//                当前导入优先级更高，更新数据
                log.info("Updating member {} from {} to {}", membershipNumber, existingDataSource, newDataSource);
                existingMember.setName(name.trim());
                existingMember.setPrimaryEmail(email.trim());
                // Keep existing token and verification code stable
                existingMember.setDataSource(newDataSource);

//                记录数据源变更日志
                log.info("Data source changed for member {}: {} -> {} at {}",
                        membershipNumber, existingDataSource, newDataSource, LocalDateTime.now());

                updateOptionalFields(existingMember, row, headerMap);
                return existingMember;
            } else {
//                现有数据优先级更高，跳过更新但记录尝试
                log.info("Skipping update for member {} - existing source {} has higher priority than {}",
                        membershipNumber, existingDataSource, newDataSource);
                throw new IllegalArgumentException(
                        String.format("Member %s exists with higher priority source (%s). Use emergency mode to override.",
                                membershipNumber, existingDataSource));
            }
        }

//注意：邮箱可以重复，只有会员号需要唯一

//        创建新会员
        Member member = Member.builder()
                .name(name.trim())
                .primaryEmail(email.trim())
                .membershipNumber(membershipNumber.trim())
                .token(UUID.randomUUID())
                .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                .hasRegistered(false)
                .isAttending(false)
                .isSpecialVote(false)
                .hasVoted(false)
                .dataSource(newDataSource)
                .build();

        log.info("Created new member {} with data source: {}", membershipNumber, newDataSource);

        updateOptionalFields(member, row, headerMap);
        return member;
    }

    // 新增：专门处理Financial Declaration with Email数据源
    private Member processFinancialDeclarationEmailJson(JsonNode memberNode, int recordNum) {
        String membershipNumber = getJsonValue(memberNode, "membershipNumber");
        String fore1 = getJsonValue(memberNode, "fore1");
        String knownAs = getJsonValue(memberNode, "knownAs");
        String surname = getJsonValue(memberNode, "surname");
        String primaryEmail = getJsonValue(memberNode, "primaryEmail");
        String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");

        // 构建完整姓名 - 优先使用knownAs
        String displayFirstName = (knownAs != null && !knownAs.trim().isEmpty()) ? knownAs.trim() :
                (fore1 != null ? fore1.trim() : "");
        String surnameTrimmed = surname != null ? surname.trim() : "";
        String fullName = (displayFirstName + " " + surnameTrimmed).trim();

        if (StringUtils.isBlank(membershipNumber)) {
            throw new IllegalArgumentException("membershipNumber is required");
        }
        if (StringUtils.isBlank(fullName)) {
            throw new IllegalArgumentException("Name information is required");
        }
        if (StringUtils.isBlank(primaryEmail)) {
            throw new IllegalArgumentException("primaryEmail is required for email members");
        }

        // 检查现有会员
        Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber.trim());

        Member member;
        if (existingMember.isPresent()) {
            member = existingMember.get();
            member.setName(fullName);
            // 统一使用primaryEmail作为主邮箱
            member.setPrimaryEmail(primaryEmail.trim());
            member.setHasEmail(true);
            member.setDataSource("INFORMER_EMAIL_MEMBERS");
        } else {
            // 新会员
            member = Member.builder()
                    .name(fullName)
                    .membershipNumber(membershipNumber.trim())
                    .primaryEmail(primaryEmail.trim())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .hasEmail(true)
                    .dataSource("INFORMER_EMAIL_MEMBERS")
                    .build();
        }

        // 映射所有Financial Declaration字段
        setOptionalJsonField(memberNode, "financialIndicatorDescription", member::setFinancialIndicator);
        setOptionalJsonField(memberNode, "fore1", member::setFore1);
        setOptionalJsonField(memberNode, "knownAs", member::setKnownAs);
        setOptionalJsonField(memberNode, "surname", member::setSurname);
        setOptionalJsonField(memberNode, "telephoneMobile", member::setTelephoneMobile);
        setOptionalJsonField(memberNode, "employeeRef", member::setEmployeeRef);
        setOptionalJsonField(memberNode, "occupation", member::setOccupation);
        setOptionalJsonField(memberNode, "siteIndustryDesc", member::setSiteIndustryDesc);
        setOptionalJsonField(memberNode, "regionDesc", member::setRegionDesc);
        setOptionalJsonField(memberNode, "ageOfMember", member::setAgeOfMember);
        setOptionalJsonField(memberNode, "genderDesc", member::setGenderDesc);
        setOptionalJsonField(memberNode, "ethnicRegionDesc", member::setEthnicRegionDesc);
        setOptionalJsonField(memberNode, "siteSubIndustryDesc", member::setSiteSubIndustryDesc);
        setOptionalJsonField(memberNode, "bargainingGroupDesc", member::setBargainingGroupDesc);
        setOptionalJsonField(memberNode, "membershipTypeDesc", member::setMembershipTypeDesc);
        setOptionalJsonField(memberNode, "epmuMemTypeDesc", member::setEpmuMemTypeDesc);
        setOptionalJsonField(memberNode, "lastPaymentDate", member::setLastPaymentDate);

        // 处理地址字段
        setOptionalJsonField(memberNode, "addRes1", member::setAddRes1);
        setOptionalJsonField(memberNode, "addRes2", member::setAddRes2);
        setOptionalJsonField(memberNode, "addRes3", member::setAddRes3);
        setOptionalJsonField(memberNode, "addRes4", member::setAddRes4);
        setOptionalJsonField(memberNode, "addRes5", member::setAddRes5);
        setOptionalJsonField(memberNode, "addResPc", member::setAddResPc);

        // 处理工作信息 - 从数组中提取第一个值
        if (memberNode.has("employerName") && memberNode.get("employerName").isArray() &&
                memberNode.get("employerName").size() > 0) {
            String employerName = memberNode.get("employerName").get(0).asText();
            if (StringUtils.isNotBlank(employerName)) {
                member.setEmployerName(employerName);
                member.setEmployer(employerName); // 保持兼容性
            }
        }

        if (memberNode.has("workplaceDesc") && memberNode.get("workplaceDesc").isArray() &&
                memberNode.get("workplaceDesc").size() > 0) {
            String workplaceDesc = memberNode.get("workplaceDesc").get(0).asText();
            if (StringUtils.isNotBlank(workplaceDesc)) {
                member.setWorkplaceDesc(workplaceDesc);
            }
        }

        if (memberNode.has("branchDesc") && memberNode.get("branchDesc").isArray() &&
                memberNode.get("branchDesc").size() > 0) {
            String branchDesc = memberNode.get("branchDesc").get(0).asText();
            if (StringUtils.isNotBlank(branchDesc)) {
                member.setBranchDesc(branchDesc);
            }
        }

        // 处理DOB
        if (memberNode.has("dob") && memberNode.get("dob").isArray() &&
                memberNode.get("dob").size() > 0) {
            String dobString = memberNode.get("dob").get(0).asText();
            if (StringUtils.isNotBlank(dobString)) {
                member.setDob(dobString);
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    LocalDate dob = LocalDate.parse(dobString, formatter);
                    member.setDobLegacy(dob);
                } catch (Exception e) {
                    log.warn("Failed to parse DOB: {}", dobString);
                }
            }
        }

        // 设置联系方式状态
        member.setHasMobile(StringUtils.isNotBlank(member.getTelephoneMobile()));
        // hasEmail已设置为true

        return member;
    }

    // 新增：专门处理Financial Declaration SMS Only数据源
    private Member processFinancialDeclarationSmsJson(JsonNode memberNode, int recordNum) {
        // SMS数据源结构与Email版本相同，只是primaryEmail为空
        String membershipNumber = getJsonValue(memberNode, "membershipNumber");
        String fore1 = getJsonValue(memberNode, "fore1");
        String knownAs = getJsonValue(memberNode, "knownAs");
        String surname = getJsonValue(memberNode, "surname");
        String primaryEmail = getJsonValue(memberNode, "primaryEmail");
        String telephoneMobile = getJsonValue(memberNode, "telephoneMobile");

        // 构建完整姓名 - 优先使用knownAs
        String displayFirstName = (knownAs != null && !knownAs.trim().isEmpty()) ? knownAs.trim() :
                (fore1 != null ? fore1.trim() : "");
        String surnameTrimmed = surname != null ? surname.trim() : "";
        String fullName = (displayFirstName + " " + surnameTrimmed).trim();

        if (StringUtils.isBlank(membershipNumber)) {
            throw new IllegalArgumentException("membershipNumber is required");
        }
        if (StringUtils.isBlank(fullName)) {
            throw new IllegalArgumentException("Name information is required");
        }

        // SMS数据源必须有手机号
        if (StringUtils.isBlank(telephoneMobile)) {
            throw new IllegalArgumentException("SMS members must have mobile phone number");
        }

        // 检查现有会员
        Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber.trim());

        Member member;
        if (existingMember.isPresent()) {
            member = existingMember.get();
            member.setName(fullName);

            // 关键改进：不覆盖现有邮箱，如果SMS源没有邮箱就保持原状
            if (StringUtils.isNotBlank(primaryEmail)) {
                member.setPrimaryEmail(primaryEmail.trim());
                member.setPrimaryEmail(primaryEmail.trim());
                member.setHasEmail(true);
            } else {
                // SMS数据源无邮箱 - 保持现有邮箱不变，但标记联系方式状态
                member.setHasEmail(StringUtils.isNotBlank(member.getPrimaryEmail()));
            }

            member.setDataSource("INFORMER_SMS_MEMBERS");
        } else {
            // 新会员 - SMS Only用户
            member = Member.builder()
                    .name(fullName)
                    .membershipNumber(membershipNumber.trim())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .dataSource("INFORMER_SMS_MEMBERS")
                    .build();

            // 处理邮箱：SMS用户可能没有邮箱
            if (StringUtils.isNotBlank(primaryEmail)) {
                member.setPrimaryEmail(primaryEmail.trim());
                member.setPrimaryEmail(primaryEmail.trim());
                member.setHasEmail(true);
            } else {
                // 关键改进：不生成假邮箱，直接设为null
                member.setPrimaryEmail(null);
                member.setPrimaryEmail(null);
                member.setHasEmail(false);
            }
        }

        // 映射所有Financial Declaration字段
        setOptionalJsonField(memberNode, "financialIndicatorDescription", member::setFinancialIndicator);
        setOptionalJsonField(memberNode, "fore1", member::setFore1);
        setOptionalJsonField(memberNode, "knownAs", member::setKnownAs);
        setOptionalJsonField(memberNode, "surname", member::setSurname);
        setOptionalJsonField(memberNode, "telephoneMobile", member::setTelephoneMobile);
        setOptionalJsonField(memberNode, "employeeRef", member::setEmployeeRef);
        setOptionalJsonField(memberNode, "occupation", member::setOccupation);
        setOptionalJsonField(memberNode, "siteIndustryDesc", member::setSiteIndustryDesc);
        setOptionalJsonField(memberNode, "regionDesc", member::setRegionDesc);
        setOptionalJsonField(memberNode, "ageOfMember", member::setAgeOfMember);
        setOptionalJsonField(memberNode, "genderDesc", member::setGenderDesc);
        setOptionalJsonField(memberNode, "ethnicRegionDesc", member::setEthnicRegionDesc);
        setOptionalJsonField(memberNode, "siteSubIndustryDesc", member::setSiteSubIndustryDesc);
        setOptionalJsonField(memberNode, "bargainingGroupDesc", member::setBargainingGroupDesc);
        setOptionalJsonField(memberNode, "membershipTypeDesc", member::setMembershipTypeDesc);
        setOptionalJsonField(memberNode, "epmuMemTypeDesc", member::setEpmuMemTypeDesc);
        setOptionalJsonField(memberNode, "lastPaymentDate", member::setLastPaymentDate);

        // 处理地址字段
        setOptionalJsonField(memberNode, "addRes1", member::setAddRes1);
        setOptionalJsonField(memberNode, "addRes2", member::setAddRes2);
        setOptionalJsonField(memberNode, "addRes3", member::setAddRes3);
        setOptionalJsonField(memberNode, "addRes4", member::setAddRes4);
        setOptionalJsonField(memberNode, "addRes5", member::setAddRes5);
        setOptionalJsonField(memberNode, "addResPc", member::setAddResPc);

        // 处理工作信息 - 从数组中提取第一个值
        if (memberNode.has("employerName") && memberNode.get("employerName").isArray() &&
                memberNode.get("employerName").size() > 0) {
            String employerName = memberNode.get("employerName").get(0).asText();
            if (StringUtils.isNotBlank(employerName)) {
                member.setEmployerName(employerName);
                member.setEmployer(employerName);
            }
        }

        if (memberNode.has("workplaceDesc") && memberNode.get("workplaceDesc").isArray() &&
                memberNode.get("workplaceDesc").size() > 0) {
            String workplaceDesc = memberNode.get("workplaceDesc").get(0).asText();
            if (StringUtils.isNotBlank(workplaceDesc)) {
                member.setWorkplaceDesc(workplaceDesc);
            }
        }

        if (memberNode.has("branchDesc") && memberNode.get("branchDesc").isArray() &&
                memberNode.get("branchDesc").size() > 0) {
            String branchDesc = memberNode.get("branchDesc").get(0).asText();
            if (StringUtils.isNotBlank(branchDesc)) {
                member.setBranchDesc(branchDesc);
            }
        }

        // 处理DOB
        if (memberNode.has("dob") && memberNode.get("dob").isArray() &&
                memberNode.get("dob").size() > 0) {
            String dobString = memberNode.get("dob").get(0).asText();
            if (StringUtils.isNotBlank(dobString)) {
                member.setDob(dobString);
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                    LocalDate dob = LocalDate.parse(dobString, formatter);
                    member.setDobLegacy(dob);
                } catch (Exception e) {
                    log.warn("Failed to parse DOB: {}", dobString);
                }
            }
        }

        // 清晰的联系方式状态设置
        member.setHasMobile(StringUtils.isNotBlank(member.getTelephoneMobile()));
        // hasEmail已在上面根据实际邮箱情况设置

        return member;
    }

    // 新增：专门处理Event Attendees数据源
    private Member processEventAttendeesJson(JsonNode memberNode, int recordNum) {
        String membershipNumber = getJsonValue(memberNode, "link_to_member_assoc_membershipNumber");
        String fore1 = getJsonValue(memberNode, "link_to_member_assoc_fore1");
        String surname = getJsonValue(memberNode, "link_to_member_assoc_surname");
        String primaryEmail = getJsonValue(memberNode, "link_to_member_assoc_primaryEmail");
        String telephoneMobile = getJsonValue(memberNode, "link_to_member_assoc_telephoneMobile");

        String fullName = (StringUtils.defaultString(fore1) + " " + StringUtils.defaultString(surname)).trim();

        if (StringUtils.isBlank(membershipNumber)) {
            throw new IllegalArgumentException("membershipNumber is required");
        }
        if (StringUtils.isBlank(fullName)) {
            throw new IllegalArgumentException("Name information is required");
        }

        // 检查现有会员
        Optional<Member> existingMember = memberRepository.findByMembershipNumber(membershipNumber.trim());

        Member member;
        if (existingMember.isPresent()) {
            member = existingMember.get();
            member.setName(fullName);
            if (StringUtils.isNotBlank(primaryEmail)) {
                member.setPrimaryEmail(primaryEmail.trim());
                member.setPrimaryEmail(primaryEmail.trim());
            }
            // 更新数据源但保持更高优先级源不被覆盖
            if (!"INFORMER_EMAIL_MEMBERS".equals(member.getDataSource()) &&
                    !"INFORMER_SMS_MEMBERS".equals(member.getDataSource())) {
                member.setDataSource("INFORMER_ATTENDEES");
            }
        } else {
            member = Member.builder()
                    .name(fullName)
                    .membershipNumber(membershipNumber.trim())
                    .token(UUID.randomUUID())
                    .verificationCode(verificationCodeGenerator.generateSixDigitCode())
                    .hasRegistered(false)
                    .isAttending(false)
                    .isSpecialVote(false)
                    .hasVoted(false)
                    .dataSource("INFORMER_ATTENDEES")
                    .build();

            if (StringUtils.isNotBlank(primaryEmail)) {
                member.setPrimaryEmail(primaryEmail.trim());
                member.setPrimaryEmail(primaryEmail.trim());
            }
        }

        // 映射Event Attendees特有字段
        setOptionalJsonField(memberNode, "link_to_member_assoc_fore1", member::setFore1);
        setOptionalJsonField(memberNode, "link_to_member_assoc_surname", member::setSurname);
        setOptionalJsonField(memberNode, "link_to_member_assoc_telephoneMobile", member::setTelephoneMobile);
        setOptionalJsonField(memberNode, "link_to_member_assoc_siteIndustryDesc", member::setSiteIndustryDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_siteSubIndustryDesc", member::setSiteSubIndustryDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_regionDesc", member::setRegionDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_ageOfMember", member::setAgeOfMember);
        setOptionalJsonField(memberNode, "link_to_member_assoc_genderDesc", member::setGenderDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_ethnicRegionDesc", member::setEthnicRegionDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_ethnicOriginDesc", member::setEthnicOriginDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_bargainingGroupDesc", member::setBargainingGroupDesc);
        setOptionalJsonField(memberNode, "link_to_member_assoc_forumDesc", member::setForumDesc);

        // Event Attendees独有字段
        setOptionalJsonField(memberNode, "link_to_member_assoc_sitePrimOrgName", member::setSitePrimOrgName);
        setOptionalJsonField(memberNode, "link_to_member_assoc_orgTeamPDescEpmu", member::setOrgTeamPDescEpmu);

        // 处理数组字段
        if (memberNode.has("link_to_member_assoc_employerName") &&
                memberNode.get("link_to_member_assoc_employerName").isArray() &&
                memberNode.get("link_to_member_assoc_employerName").size() > 0) {
            String employerName = memberNode.get("link_to_member_assoc_employerName").get(0).asText();
            if (StringUtils.isNotBlank(employerName)) {
                member.setEmployerName(employerName);
                member.setEmployer(employerName);
            }
        }

        if (memberNode.has("link_to_member_assoc_workplaceDesc") &&
                memberNode.get("link_to_member_assoc_workplaceDesc").isArray() &&
                memberNode.get("link_to_member_assoc_workplaceDesc").size() > 0) {
            String workplaceDesc = memberNode.get("link_to_member_assoc_workplaceDesc").get(0).asText();
            if (StringUtils.isNotBlank(workplaceDesc)) {
                member.setWorkplaceDesc(workplaceDesc);
            }
        }

        if (memberNode.has("link_to_member_assoc_branchDesc") &&
                memberNode.get("link_to_member_assoc_branchDesc").isArray() &&
                memberNode.get("link_to_member_assoc_branchDesc").size() > 0) {
            String branchDesc = memberNode.get("link_to_member_assoc_branchDesc").get(0).asText();
            if (StringUtils.isNotBlank(branchDesc)) {
                member.setBranchDesc(branchDesc);
            }
        }

        if (memberNode.has("link_to_member_assoc_link_to_workplace_assoc_xsubIndSector") &&
                memberNode.get("link_to_member_assoc_link_to_workplace_assoc_xsubIndSector").isArray() &&
                memberNode.get("link_to_member_assoc_link_to_workplace_assoc_xsubIndSector").size() > 0) {
            String subIndSector = memberNode.get("link_to_member_assoc_link_to_workplace_assoc_xsubIndSector").get(0).asText();
            if (StringUtils.isNotBlank(subIndSector)) {
                member.setSubIndSector(subIndSector);
            }
        }

        if (memberNode.has("link_to_member_assoc_link_to_sites_assoc_directorName") &&
                memberNode.get("link_to_member_assoc_link_to_sites_assoc_directorName").isArray() &&
                memberNode.get("link_to_member_assoc_link_to_sites_assoc_directorName").size() > 0) {
            String directorName = memberNode.get("link_to_member_assoc_link_to_sites_assoc_directorName").get(0).asText();
            if (StringUtils.isNotBlank(directorName)) {
                member.setDirectorName(directorName);
            }
        }

        // 设置联系方式状态
        member.setHasEmail(StringUtils.isNotBlank(member.getPrimaryEmail()));
        member.setHasMobile(StringUtils.isNotBlank(member.getTelephoneMobile()));

        return member;
    }

    // 增强processJsonData方法以支持自动识别数据源类型
    private ImportResponse processJsonDataEnhanced(String jsonData, String expectedDataSource) {
        List<String> errors = new ArrayList<>();
        List<Member> membersToSave = new ArrayList<>();
        int totalRecords = 0;
        int successCount = 0;
        int failureCount = 0;

        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);
            JsonNode dataArray = rootNode;

            if (rootNode.has("data")) {
                dataArray = rootNode.get("data");
            }

            if (!dataArray.isArray()) {
                throw new IllegalArgumentException("Response data is not an array");
            }

            totalRecords = dataArray.size();
            log.info("Processing {} records", totalRecords);

            for (int i = 0; i < dataArray.size(); i++) {
                JsonNode memberNode = dataArray.get(i);
                try {
                    // Process all members using the same logic
                    Member member = processRegularMemberJson(memberNode, i + 1);

                    membersToSave.add(member);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    errors.add("Record " + (i + 1) + ": " + e.getMessage());
                    log.error("Failed to process record {}: {}", i + 1, e.getMessage());
                }
            }

            if (!membersToSave.isEmpty()) {
                log.info("Saving {} members to database", membersToSave.size());
                memberRepository.saveAll(membersToSave);
            }

        } catch (Exception e) {
            log.error("JSON data processing failed: {}", e.getMessage(), e);
            return ImportResponse.builder()
                    .total(0)
                    .success(0)
                    .failed(1)
                    .errors(Arrays.asList("Data processing failed: " + e.getMessage()))
                    .build();
        }

        return ImportResponse.builder()
                .total(totalRecords)
                .success(successCount)
                .failed(failureCount)
                .errors(errors)
                .build();
    }
}