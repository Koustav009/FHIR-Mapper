/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.services;

import in.nha.abdm.fhir.mapper.rest.requests.CommunicationBundleRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.CommunicationPayloadResource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Communication;
import org.springframework.stereotype.Service;

@Service
public class CommunicationBundleExtractor extends FhirExtractionSupport {

  public CommunicationBundleRequest extract(Communication communication) {
    return CommunicationBundleRequest.builder()
        .identifier(
            communication.hasIdentifier() ? communication.getIdentifierFirstRep().getValue() : null)
        .basedOn(
            communication.hasBasedOn() ? communication.getBasedOnFirstRep().getReference() : null)
        .status(communication.hasStatus() ? communication.getStatus().toCode() : null)
        .category(
            communication.hasCategory()
                ? firstCodingCode(communication.getCategoryFirstRep())
                : null)
        .priority(communication.hasPriority() ? communication.getPriority().toCode() : null)
        .senderFacilityId(
            communication.hasSender() ? referenceDisplay(communication.getSender()) : null)
        .recipientFacilityId(
            communication.hasRecipient()
                ? referenceDisplay(communication.getRecipientFirstRep())
                : null)
        .reasonCode(
            communication.hasReasonCode()
                ? conceptText(communication.getReasonCodeFirstRep())
                : null)
        .reasonReference(
            communication.hasReasonReference()
                ? communication.getReasonReferenceFirstRep().getReference()
                : null)
        .payload(payloads(communication))
        .build();
  }

  private List<CommunicationPayloadResource> payloads(Communication communication) {
    List<CommunicationPayloadResource> payloads = new ArrayList<>();
    for (Communication.CommunicationPayloadComponent payload : communication.getPayload()) {
      if (!(payload.getContent() instanceof Attachment attachment)) {
        continue;
      }
      payloads.add(
          CommunicationPayloadResource.builder()
              .contentType(attachment.getContentType())
              .data(
                  attachment.hasData()
                      ? Base64.getEncoder().encodeToString(attachment.getData())
                      : null)
              .title(attachment.getTitle())
              .language(attachment.getLanguage())
              .creation(
                  attachment.hasCreationElement()
                      ? attachment.getCreationElement().getValueAsString()
                      : null)
              .build());
    }
    return payloads;
  }
}
