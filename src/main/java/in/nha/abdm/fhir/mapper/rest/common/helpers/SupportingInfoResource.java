/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.common.helpers;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Supporting information document attached to the claim")
public class SupportingInfoResource {

  @Schema(description = "Title of the supporting document", example = "Discharge summary")
  private String title;

  @Schema(description = "Base64-encoded document content")
  private String data;

  @Schema(description = "MIME type of the document", example = "application/pdf")
  private String contentType;

  @Schema(description = "Supporting information category code", example = "info")
  private String categoryCode;

  @Schema(description = "Supporting information code", example = "document")
  private String informationCode;
}
