/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.dto.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.nha.abdm.fhir.mapper.rest.common.constants.BundleUrlIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.database.h2.services.SnomedService;
import in.nha.abdm.fhir.mapper.rest.requests.CommunicationBundleRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.CommunicationPayloadResource;
import java.util.Base64;
import java.util.List;
import org.hl7.fhir.r4.model.Communication;
import org.junit.jupiter.api.Test;

class MakeCommunicationResourceTest {

  private final MakeCommunicationResource target = new MakeCommunicationResource(null);

  private CommunicationBundleRequest.CommunicationBundleRequestBuilder baseRequest() {
    return CommunicationBundleRequest.builder()
        .identifier("4524657454")
        .status("completed")
        .category("notification")
        .priority("routine")
        .senderFacilityId("HOSP-1")
        .recipientFacilityId("INS-1")
        .payload(
            List.of(
                CommunicationPayloadResource.builder()
                    .contentType("application/pdf")
                    .data(Base64.getEncoder().encodeToString("report".getBytes()))
                    .title("Angiography report PDF")
                    .language("en-IN")
                    .creation("2023-12-08T14:58:58.181+05:30")
                    .build()));
  }

  @Test
  void mapsFlatRequestToCommunication() throws Exception {
    Communication communication = target.getCommunication(baseRequest().build());

    assertEquals(
        ResourceProfileIdentifier.PROFILE_COMMUNICATION,
        communication.getMeta().getProfile().get(0).getValue());
    assertEquals(Communication.CommunicationStatus.COMPLETED, communication.getStatus());
    assertEquals(
        ResourceProfileIdentifier.COMMUNICATION_CATEGORY_SYSTEM,
        communication.getCategoryFirstRep().getCodingFirstRep().getSystem());
    assertEquals("notification", communication.getCategoryFirstRep().getCodingFirstRep().getCode());
    assertEquals(Communication.CommunicationPriority.ROUTINE, communication.getPriority());
    assertEquals("4524657454", communication.getIdentifierFirstRep().getValue());
    assertEquals("HOSP-1", communication.getSender().getDisplay());
    assertEquals("INS-1", communication.getRecipientFirstRep().getDisplay());
    assertTrue(communication.hasText());
  }

  @Test
  void mapsReferencesAsLiteralReferences() throws Exception {
    Communication communication =
        target.getCommunication(
            baseRequest()
                .basedOn("CommunicationRequest/example-01")
                .reasonReference("Condition/example-03")
                .build());

    assertEquals(
        "CommunicationRequest/example-01", communication.getBasedOnFirstRep().getReference());
    assertEquals("Condition/example-03", communication.getReasonReferenceFirstRep().getReference());
  }

  @Test
  void carriesPayloadAttachment() throws Exception {
    Communication communication = target.getCommunication(baseRequest().build());

    assertEquals(1, communication.getPayload().size());
    assertEquals(
        "application/pdf",
        communication.getPayloadFirstRep().getContentAttachment().getContentType());
    assertEquals(
        "report", new String(communication.getPayloadFirstRep().getContentAttachment().getData()));
    assertEquals(
        "Angiography report PDF",
        communication.getPayloadFirstRep().getContentAttachment().getTitle());
    assertEquals("en-IN", communication.getPayloadFirstRep().getContentAttachment().getLanguage());
    assertEquals(
        "2023-12-08T09:28:58.181Z",
        communication
            .getPayloadFirstRep()
            .getContentAttachment()
            .getCreationElement()
            .getValueAsString());
  }

  @Test
  void mapsReasonCodeThroughSnomed() throws Exception {
    SnomedService snomedService =
        new SnomedService(null, null, null, null, null, null, null, null) {
          @Override
          public in.nha.abdm.fhir.mapper.rest.database.h2.tables.SnomedConditionProcedure
              getConditionProcedureCode(String display) {
            return in.nha.abdm.fhir.mapper.rest.database.h2.tables.SnomedConditionProcedure
                .builder()
                .code("410429000")
                .display("Cardiac arrest")
                .build();
          }
        };
    Communication communication =
        new MakeCommunicationResource(snomedService)
            .getCommunication(baseRequest().reasonCode("Cardiac arrest").build());

    assertEquals(
        BundleUrlIdentifier.SNOMED_URL,
        communication.getReasonCodeFirstRep().getCodingFirstRep().getSystem());
    assertEquals("410429000", communication.getReasonCodeFirstRep().getCodingFirstRep().getCode());
    assertEquals("Cardiac arrest", communication.getReasonCodeFirstRep().getText());
  }
}
