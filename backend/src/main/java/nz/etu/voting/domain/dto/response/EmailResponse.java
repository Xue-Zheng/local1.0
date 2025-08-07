package nz.etu.voting.domain.dto.response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    private int total;
    private int sent;
    private int failed;
}
