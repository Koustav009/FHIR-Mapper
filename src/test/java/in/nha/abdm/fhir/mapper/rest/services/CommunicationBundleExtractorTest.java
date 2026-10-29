/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.nha.abdm.fhir.mapper.rest.common.constants.BundleUrlIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.requests.CommunicationBundleRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.CommunicationPayloadResource;
import java.util.Base64;
import java.util.List;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Communication;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class CommunicationBundleExtractorTest {

  private final CommunicationBundleExtractor extractor = new CommunicationBundleExtractor();

  @Test
  void reversesFlatFieldsAndPayloadAttachment() {
    Communication communication = new Communication();
    communication.setId("comm-1");
    communication.setStatus(Communication.CommunicationStatus.COMPLETED);
    communication.addIdentifier().setValue("4524657454");
    communication.addBasedOn().setReference("CommunicationRequest/example-01");
    communication
        .addCategory()
        .addCoding()
        .setSystem(ResourceProfileIdentifier.COMMUNICATION_CATEGORY_SYSTEM)
        .setCode("notification");
    communication.setPriority(Communication.CommunicationPriority.ROUTINE);
    communication.setSender(new Reference().setDisplay("HOSP-1"));
    communication.addRecipient(new Reference().setDisplay("INS-1"));
    communication
        .addReasonCode()
        .setText("Cardiac arrest")
        .addCoding()
        .setSystem(BundleUrlIdentifier.SNOMED_URL)
        .setCode("410429000")
        .setDisplay("Cardiac arrest");
    communication.addReasonReference().setReference("Condition/example-03");
    communication
        .addPayload()
        .setContent(
            new Attachment()
                .setContentType("application/pdf")
                .setData(Base64.getDecoder().decode("cmVwb3J0"))
                .setTitle("Angiography report PDF")
                .setLanguage("en-IN")
                .setCreationElement(new DateTimeType("2023-12-08T09:28:58.181Z")));

    CommunicationBundleRequest request = extractor.extract(communication);

    assertEquals("4524657454", request.getIdentifier());
    assertEquals("CommunicationRequest/example-01", request.getBasedOn());
    assertEquals("completed", request.getStatus());
    assertEquals("notification", request.getCategory());
    assertEquals("routine", request.getPriority());
    assertEquals("HOSP-1", request.getSenderFacilityName());
    assertEquals("INS-1", request.getRecipientFacilityName());
    assertEquals("Cardiac arrest", request.getReasonCode());
    assertEquals("Condition/example-03", request.getReasonReference());

    List<CommunicationPayloadResource> payloads = request.getPayload();
    assertEquals(1, payloads.size());
    assertEquals("application/pdf", payloads.get(0).getContentType());
    assertEquals("cmVwb3J0", payloads.get(0).getData());
    assertEquals("Angiography report PDF", payloads.get(0).getTitle());
    assertEquals("en-IN", payloads.get(0).getLanguage());
    assertEquals("2023-12-08T09:28:58.181Z", payloads.get(0).getCreation());
  }
}
