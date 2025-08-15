package nz.etu.voting.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResponse {

    private int total;
    private int success;
    private int failed;
    private List<String> errors;
}