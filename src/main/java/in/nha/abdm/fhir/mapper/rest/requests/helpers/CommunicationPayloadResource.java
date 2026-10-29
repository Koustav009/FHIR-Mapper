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
@Schema(description = "Attachment payload carried by a Communication resource")
public class CommunicationPayloadResource {
  @Schema(description = "MIME type of the payload", example = "application/pdf")
  @NotBlank(message = "contentType is mandatory")
  private String contentType;

  @Schema(description = "Base64 encoded payload", example = "JVBERi0xLjcNCiW1tbW1DQo...")
  @NotBlank(message = "data is mandatory")
  private String data;

  @Schema(description = "Human readable title of the payload", example = "Angiography report PDF")
  private String title;

  @Schema(description = "Language of the payload", example = "en-IN")
  private String language;

  @Schema(
      description = "Creation timestamp of the payload",
      example = "2023-12-08T14:58:58.181+05:30")
  private String creation;
}
