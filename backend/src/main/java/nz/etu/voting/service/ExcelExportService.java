package nz.etu.voting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nz.etu.voting.domain.entity.Event;
import nz.etu.voting.domain.entity.EventMember;
import nz.etu.voting.domain.entity.Member;
import nz.etu.voting.domain.entity.NotificationLog;
import nz.etu.voting.repository.EventMemberRepository;
import nz.etu.voting.repository.MemberRepository;
import nz.etu.voting.repository.NotificationLogRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final EventMemberRepository eventMemberRepository;
    private final MemberRepository memberRepository;
    private final NotificationLogRepository notificationLogRepository;

    public byte[] exportEventMembersToExcel(Event event) throws IOException {
        List<EventMember> members = eventMemberRepository.findByEvent(event);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Event Members");


            CellStyle headerStyle = createHeaderStyle(workbook);


            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Membership Number", "Name", "Email", "Mobile Phone",
                    "Has Email", "Has Mobile", "Registered", "Attending",
                    "Special Vote", "Voted", "Checked In", "Absence Reason",
                    "Registration Date", "Check In Time"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }


            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (EventMember member : members) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(member.getMembershipNumber());
                row.createCell(1).setCellValue(member.getName());
                row.createCell(2).setCellValue(member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "");
                row.createCell(3).setCellValue(member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "");
                row.createCell(4).setCellValue(member.getHasEmail() ? "Yes" : "No");
                row.createCell(5).setCellValue(member.getHasMobile() ? "Yes" : "No");
                row.createCell(6).setCellValue(member.getHasRegistered() ? "Yes" : "No");
                row.createCell(7).setCellValue(member.getIsAttending() ? "Yes" : "No");
                row.createCell(8).setCellValue(member.getIsSpecialVote() ? "Yes" : "No");
                row.createCell(9).setCellValue(member.getHasVoted() ? "Yes" : "No");
                row.createCell(10).setCellValue(member.getCheckedIn() ? "Yes" : "No");
                row.createCell(11).setCellValue(member.getAbsenceReason() != null ? member.getAbsenceReason() : "");
                row.createCell(12).setCellValue(member.getCreatedAt() != null ? member.getCreatedAt().format(formatter) : "");
                row.createCell(13).setCellValue(member.getCheckInTime() != null ? member.getCheckInTime().format(formatter) : "");
            }


            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] exportFilteredMembersToExcel(List<Member> members) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Filtered Members");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Membership Number", "Name", "Primary Email", "Mobile Phone", "Financial Indicator",
                    "First Name", "Known As", "Surname", "DOB", "Employee Ref", "Occupation",
                    "Address Line 1", "Address Line 2", "Address Line 3", "Address Line 4", "Address Line 5", "Postcode",
                    "Has Email", "Has Mobile", "Registered", "Attending",
                    "Special Vote", "Voted", "Checked In", "Region", "Branch", "Forum",
                    "Industry", "Sub Industry", "Employer", "Workplace", "Last Payment Date",
                    "Membership Type", "EPMU Member Type", "Age", "Gender", "Ethnic Region",
                    "Bargaining Group", "Data Source", "Created At"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (Member member : members) {
                Row row = sheet.createRow(rowNum++);
                int cellIndex = 0;

                // Basic info
                row.createCell(cellIndex++).setCellValue(member.getMembershipNumber() != null ? member.getMembershipNumber() : "");
                row.createCell(cellIndex++).setCellValue(member.getName() != null ? member.getName() : "");
                row.createCell(cellIndex++).setCellValue(member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "");
                row.createCell(cellIndex++).setCellValue(member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "");
                row.createCell(cellIndex++).setCellValue(member.getFinancialIndicator() != null ? member.getFinancialIndicator() : "");

                // Personal details
                row.createCell(cellIndex++).setCellValue(member.getFore1() != null ? member.getFore1() : "");
                row.createCell(cellIndex++).setCellValue(member.getKnownAs() != null ? member.getKnownAs() : "");
                row.createCell(cellIndex++).setCellValue(member.getSurname() != null ? member.getSurname() : "");
                row.createCell(cellIndex++).setCellValue(member.getDob() != null ? member.getDob() : "");
                row.createCell(cellIndex++).setCellValue(member.getEmployeeRef() != null ? member.getEmployeeRef() : "");
                row.createCell(cellIndex++).setCellValue(member.getOccupation() != null ? member.getOccupation() : "");

                // Address
                row.createCell(cellIndex++).setCellValue(member.getAddRes1() != null ? member.getAddRes1() : "");
                row.createCell(cellIndex++).setCellValue(member.getAddRes2() != null ? member.getAddRes2() : "");
                row.createCell(cellIndex++).setCellValue(member.getAddRes3() != null ? member.getAddRes3() : "");
                row.createCell(cellIndex++).setCellValue(member.getAddRes4() != null ? member.getAddRes4() : "");
                row.createCell(cellIndex++).setCellValue(member.getAddRes5() != null ? member.getAddRes5() : "");
                row.createCell(cellIndex++).setCellValue(member.getAddResPc() != null ? member.getAddResPc() : "");

                // Status fields
                row.createCell(cellIndex++).setCellValue(member.getHasEmail() != null ? (member.getHasEmail() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasMobile() != null ? (member.getHasMobile() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasRegistered() != null ? (member.getHasRegistered() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getIsAttending() != null ? (member.getIsAttending() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getIsSpecialVote() != null ? (member.getIsSpecialVote() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasVoted() != null ? (member.getHasVoted() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getCheckinTime() != null ? "Yes" : "No");

                // Organization info
                row.createCell(cellIndex++).setCellValue(member.getRegionDesc() != null ? member.getRegionDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getBranchDesc() != null ? member.getBranchDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getForumDesc() != null ? member.getForumDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getSiteIndustryDesc() != null ? member.getSiteIndustryDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getSiteSubIndustryDesc() != null ? member.getSiteSubIndustryDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getEmployerName() != null ? member.getEmployerName() : "");
                row.createCell(cellIndex++).setCellValue(member.getWorkplaceDesc() != null ? member.getWorkplaceDesc() : "");

                // Membership details
                row.createCell(cellIndex++).setCellValue(member.getLastPaymentDate() != null ? member.getLastPaymentDate() : "");
                row.createCell(cellIndex++).setCellValue(member.getMembershipTypeDesc() != null ? member.getMembershipTypeDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getEpmuMemTypeDesc() != null ? member.getEpmuMemTypeDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getAgeOfMember() != null ? member.getAgeOfMember() : "");
                row.createCell(cellIndex++).setCellValue(member.getGenderDesc() != null ? member.getGenderDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getEthnicRegionDesc() != null ? member.getEthnicRegionDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getBargainingGroupDesc() != null ? member.getBargainingGroupDesc() : "");

                // System fields
                row.createCell(cellIndex++).setCellValue(member.getDataSource() != null ? member.getDataSource() : "");
                row.createCell(cellIndex++).setCellValue(member.getCreatedAt() != null ? member.getCreatedAt().format(formatter) : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    //    exportEventAttendeesToExcel method removed - EventAttendee table no longer exists
//    All attendee data is now available through EventMember table
    public byte[] exportNotificationLogsToExcel(Event event) throws IOException {
        List<NotificationLog> logs = notificationLogRepository.findByEvent(event);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Notification Logs");


            CellStyle headerStyle = createHeaderStyle(workbook);


            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Recipient", "Type", "Status", "Subject", "Content Preview",
                    "Sent At", "Error Message", "Retry Count"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }


            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (NotificationLog log : logs) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(log.getRecipient() != null ? log.getRecipient() : "");
                row.createCell(1).setCellValue(log.getNotificationType() != null ? log.getNotificationType().toString() : "");
                row.createCell(2).setCellValue(log.getIsSuccessful() != null ? (log.getIsSuccessful() ? "Success" : "Failed") : "Failed");
                row.createCell(3).setCellValue(log.getSubject() != null ? log.getSubject() : "");
                row.createCell(4).setCellValue(log.getContent() != null ?
                        (log.getContent().length() > 100 ? log.getContent().substring(0, 100) + "..." : log.getContent()) : "");
                row.createCell(5).setCellValue(log.getSentTime() != null ? log.getSentTime().format(formatter) : "");
                row.createCell(6).setCellValue(log.getErrorMessage() != null ? log.getErrorMessage() : "");
                row.createCell(7).setCellValue("0"); // NotificationLog doesn't have retry count field
            }


            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] exportFilteredEventMembersToExcel(List<EventMember> members, Event event) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Event Members");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Membership Number", "Name", "Email", "Mobile Phone", "Payroll Number", "Site Number",
                    "Has Email", "Has Mobile", "Registered", "Attending", "Special Vote", "Voted", "Checked In",
                    "Region", "Workplace", "Employer", "Branch", "Absence Reason", "Registration Date", "Check In Time"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (EventMember member : members) {
                Row row = sheet.createRow(rowNum++);
                int cellIndex = 0;

                row.createCell(cellIndex++).setCellValue(member.getMembershipNumber() != null ? member.getMembershipNumber() : "");
                row.createCell(cellIndex++).setCellValue(member.getName() != null ? member.getName() : "");
                row.createCell(cellIndex++).setCellValue(member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "");
                row.createCell(cellIndex++).setCellValue(member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "");

                // Add payroll number and site number from linked Member
                row.createCell(cellIndex++).setCellValue(member.getMember() != null && member.getMember().getPayrollNumber() != null ?
                        member.getMember().getPayrollNumber() : "");
                row.createCell(cellIndex++).setCellValue(member.getMember() != null && member.getMember().getSiteNumber() != null ?
                        member.getMember().getSiteNumber() : "");

                row.createCell(cellIndex++).setCellValue(member.getHasEmail() != null ? (member.getHasEmail() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasMobile() != null ? (member.getHasMobile() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasRegistered() != null ? (member.getHasRegistered() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getIsAttending() != null ? (member.getIsAttending() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getIsSpecialVote() != null ? (member.getIsSpecialVote() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasVoted() != null ? (member.getHasVoted() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getCheckedIn() != null ? (member.getCheckedIn() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getRegionDesc() != null ? member.getRegionDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getWorkplace() != null ? member.getWorkplace() : "");
                row.createCell(cellIndex++).setCellValue(member.getEmployer() != null ? member.getEmployer() : "");
                row.createCell(cellIndex++).setCellValue(member.getBranch() != null ? member.getBranch() : "");
                row.createCell(cellIndex++).setCellValue(member.getAbsenceReason() != null ? member.getAbsenceReason() : "");
                row.createCell(cellIndex++).setCellValue(member.getCreatedAt() != null ? member.getCreatedAt().format(formatter) : "");
                row.createCell(cellIndex++).setCellValue(member.getCheckInTime() != null ? member.getCheckInTime().format(formatter) : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] exportCheckinDataToExcel(List<EventMember> members, Event event, String category) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Check-in Data");

            CellStyle headerStyle = createHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Membership Number", "Name", "Email", "Mobile Phone", "Payroll Number", "Site Number",
                    "Check In Time", "Check In Status", "Region", "Workplace", "Employer", "Branch",
                    "Registration Status", "Attendance Status", "Special Vote", "Voted Status"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;

            for (EventMember member : members) {
                Row row = sheet.createRow(rowNum++);
                int cellIndex = 0;

                row.createCell(cellIndex++).setCellValue(member.getMembershipNumber() != null ? member.getMembershipNumber() : "");
                row.createCell(cellIndex++).setCellValue(member.getName() != null ? member.getName() : "");
                row.createCell(cellIndex++).setCellValue(member.getPrimaryEmail() != null ? member.getPrimaryEmail() : "");
                row.createCell(cellIndex++).setCellValue(member.getTelephoneMobile() != null ? member.getTelephoneMobile() : "");

                // Add payroll number and site number from linked Member
                row.createCell(cellIndex++).setCellValue(member.getMember() != null && member.getMember().getPayrollNumber() != null ?
                        member.getMember().getPayrollNumber() : "");
                row.createCell(cellIndex++).setCellValue(member.getMember() != null && member.getMember().getSiteNumber() != null ?
                        member.getMember().getSiteNumber() : "");

                row.createCell(cellIndex++).setCellValue(member.getCheckInTime() != null ? member.getCheckInTime().format(formatter) : "");
                row.createCell(cellIndex++).setCellValue(member.getCheckedIn() != null ? (member.getCheckedIn() ? "Checked In" : "Not Checked In") : "Not Checked In");
                row.createCell(cellIndex++).setCellValue(member.getRegionDesc() != null ? member.getRegionDesc() : "");
                row.createCell(cellIndex++).setCellValue(member.getWorkplace() != null ? member.getWorkplace() : "");
                row.createCell(cellIndex++).setCellValue(member.getEmployer() != null ? member.getEmployer() : "");
                row.createCell(cellIndex++).setCellValue(member.getBranch() != null ? member.getBranch() : "");
                row.createCell(cellIndex++).setCellValue(member.getHasRegistered() != null ? (member.getHasRegistered() ? "Registered" : "Not Registered") : "Not Registered");
                row.createCell(cellIndex++).setCellValue(member.getIsAttending() != null ? (member.getIsAttending() ? "Attending" : "Not Attending") : "Not Attending");
                row.createCell(cellIndex++).setCellValue(member.getIsSpecialVote() != null ? (member.getIsSpecialVote() ? "Yes" : "No") : "No");
                row.createCell(cellIndex++).setCellValue(member.getHasVoted() != null ? (member.getHasVoted() ? "Voted" : "Not Voted") : "Not Voted");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        return style;
    }
}