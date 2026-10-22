/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.requests;

import in.nha.abdm.fhir.mapper.rest.common.constants.ValidationConstants;
import in.nha.abdm.fhir.mapper.rest.common.helpers.OrganisationResource;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.TaskInputResource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
    description =
        "Request to build an NHCX "
            + ValidationConstants.TASK_BUNDLE
            + " for an InsurancePlan request")
public class TaskBundleInsurancePlanRequest {
  @Schema(description = "Bundle type", example = ValidationConstants.TASK_BUNDLE)
  @NotBlank(message = "bundleType must be " + ValidationConstants.TASK_BUNDLE)
  private String bundleType;

  @Schema(
      description = "Care context reference / correlation id",
      example = "bc3c6c57-2053-4d0e-ac40-139ccccff645")
  @NotBlank(message = ValidationConstants.CARE_CONTEXT_MANDATORY)
  private String careContextReference;

  @Schema(description = "Task description; defaults to the insurance-plan request text")
  private String description;

  @Schema(description = "Provider (hospital) organisation requesting the plan")
  @Valid
  @NotNull(message = ValidationConstants.REQUESTER_MANDATORY) private OrganisationResource requester;

  @Schema(description = "Insurer organisation that owns the plan")
  @Valid
  @NotNull(message = ValidationConstants.OWNER_MANDATORY) private OrganisationResource owner;

  @Schema(description = "Policy number to look up", example = "ABC123456789")
  @NotBlank(message = ValidationConstants.POLICY_NUMBER_MANDATORY)
  private String policyNumber;

  @Schema(description = "Provider identifier", example = "1234567890")
  @NotBlank(message = ValidationConstants.PROVIDER_ID_MANDATORY)
  private String providerId;

  @Schema(description = "Additional Task.input entries")
  @Valid
  private List<TaskInputResource> additionalInputs;
}
