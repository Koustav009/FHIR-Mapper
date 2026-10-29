/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.requests;

import in.nha.abdm.fhir.mapper.rest.common.constants.ValidationConstants;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.CommunicationPayloadResource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Flat request to build an NRCES Communication resource")
public class CommunicationBundleRequest {
  @Schema(description = "Communication identifier", example = "4524657454")
  private String identifier;

  @Schema(
      description = "Reference to the originating CommunicationRequest",
      example = "CommunicationRequest/example-01")
  private String basedOn;

  @Schema(description = "Status of the communication", example = "completed")
  @NotBlank(message = ValidationConstants.STATUS_MANDATORY)
  @Pattern(
      regexp = ValidationConstants.COMMUNICATION_STATUS_PATTERN,
      message = ValidationConstants.COMMUNICATION_STATUS_MESSAGE)
  private String status;

  @Schema(description = "Category of the communication", example = "notification")
  @Pattern(
      regexp = ValidationConstants.COMMUNICATION_CATEGORY_PATTERN,
      message = ValidationConstants.COMMUNICATION_CATEGORY_MESSAGE)
  private String category;

  @Schema(description = "Priority of the communication", example = "routine")
  @Pattern(
      regexp = ValidationConstants.COMMUNICATION_PRIORITY_PATTERN,
      message = ValidationConstants.COMMUNICATION_PRIORITY_MESSAGE)
  private String priority;

  @Schema(description = "Name of the sender organisation")
  private String senderFacilityName;

  @Schema(description = "Name of the recipient organisation")
  private String recipientFacilityName;

  @Schema(description = "Free text reason, resolved against SNOMED")
  private String reasonCode;

  @Schema(
      description = "Reference to the Condition that is the reason for the communication",
      example = "Condition/example-03")
  private String reasonReference;

  @Schema(description = "Attachments carried by the communication")
  @Valid
  @NotEmpty(message = ValidationConstants.COMMUNICATION_PAYLOAD_MANDATORY)
  private List<CommunicationPayloadResource> payload;
}
