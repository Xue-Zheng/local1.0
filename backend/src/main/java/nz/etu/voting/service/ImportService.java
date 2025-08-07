package nz.etu.voting.service;

import nz.etu.voting.domain.dto.response.ImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ImportService {
    ImportResponse importMembersFromCsv(MultipartFile file);

    ImportResponse importMembersFromCsvWithDetails(MultipartFile file);

    ImportResponse importFromToken(String tokenInput);
}