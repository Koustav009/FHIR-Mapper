/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.requests.helpers;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Additional Task.input entry")
public class TaskInputResource {
  @Schema(description = "Input type code", example = "policyNumber")
  @NotBlank(message = "typeCode is mandatory")
  private String typeCode;

  @Schema(description = "Input type display", example = "PolicyNumber")
  private String typeDisplay;

  @Schema(description = "Input value", example = "ABC123456789")
  @NotBlank(message = "valueString is mandatory")
  private String valueString;
}
