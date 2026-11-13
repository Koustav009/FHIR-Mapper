/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.requests.helpers;

import in.nha.abdm.fhir.mapper.rest.common.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Schema(description = "A procedure performed as part of the claimed treatment")
public class ClaimProcedureResource {
  @Schema(description = "Procedure name/display", example = "Total Knee Replacement (Unilateral)")
  @NotBlank(message = "procedure is mandatory")
  private String procedure;

  @Schema(description = "Procedure type: conservative | medical | surgical", example = "surgical")
  @NotBlank(message = "procedureType is mandatory")
  @Pattern(
      regexp = ValidationConstants.PROCEDURE_TYPE_PATTERN,
      message = ValidationConstants.PROCEDURE_TYPE_MESSAGE)
  private String procedureType;
}
