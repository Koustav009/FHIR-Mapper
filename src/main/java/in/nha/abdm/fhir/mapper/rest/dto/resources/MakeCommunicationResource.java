/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.dto.resources;

import in.nha.abdm.fhir.mapper.Utils;
import in.nha.abdm.fhir.mapper.rest.common.constants.BundleUrlIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.database.h2.services.SnomedService;
import in.nha.abdm.fhir.mapper.rest.database.h2.tables.SnomedConditionProcedure;
import in.nha.abdm.fhir.mapper.rest.requests.CommunicationBundleRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.CommunicationPayloadResource;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Communication;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MakeCommunicationResource {

  private final SnomedService snomedService;

  public Communication getCommunication(CommunicationBundleRequest request) throws ParseException {
    Communication communication = new Communication();
    communication.setId(UUID.randomUUID().toString());
    communication.setMeta(buildMeta());
    communication.setStatus(Communication.CommunicationStatus.fromCode(request.getStatus()));

    if (StringUtils.isNotBlank(request.getIdentifier())) {
      communication.addIdentifier(new Identifier().setValue(request.getIdentifier()));
    }
    if (StringUtils.isNotBlank(request.getBasedOn())) {
      communication.addBasedOn(new Reference().setReference(request.getBasedOn()));
    }
    if (StringUtils.isNotBlank(request.getCategory())) {
      communication.addCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(ResourceProfileIdentifier.COMMUNICATION_CATEGORY_SYSTEM)
                      .setCode(request.getCategory())));
    }
    if (StringUtils.isNotBlank(request.getPriority())) {
      communication.setPriority(
          Communication.CommunicationPriority.fromCode(request.getPriority()));
    }
    if (StringUtils.isNotBlank(request.getSenderFacilityName())) {
      communication.setSender(new Reference().setDisplay(request.getSenderFacilityName()));
    }
    if (StringUtils.isNotBlank(request.getRecipientFacilityName())) {
      communication.addRecipient(new Reference().setDisplay(request.getRecipientFacilityName()));
    }
    if (StringUtils.isNotBlank(request.getReasonCode())) {
      communication.addReasonCode(buildReasonCode(request.getReasonCode()));
    }
    if (StringUtils.isNotBlank(request.getReasonReference())) {
      communication.addReasonReference(new Reference().setReference(request.getReasonReference()));
    }
    addPayloads(communication, request);

    Utils.setNarrative(communication, "Communication: " + communication.getStatus().getDisplay());
    return communication;
  }

  private CodeableConcept buildReasonCode(String reason) {
    SnomedConditionProcedure snomed = snomedService.getConditionProcedureCode(reason);
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setSystem(BundleUrlIdentifier.SNOMED_URL)
                .setCode(snomed.getCode())
                .setDisplay(snomed.getDisplay()))
        .setText(reason);
  }

  private void addPayloads(Communication communication, CommunicationBundleRequest request)
      throws ParseException {
    if (Objects.isNull(request.getPayload())) {
      return;
    }
    for (CommunicationPayloadResource payload : request.getPayload()) {
      Attachment attachment =
          new Attachment()
              .setContentType(payload.getContentType())
              .setData(java.util.Base64.getDecoder().decode(payload.getData()));
      if (StringUtils.isNotBlank(payload.getTitle())) {
        attachment.setTitle(payload.getTitle());
      }
      if (StringUtils.isNotBlank(payload.getLanguage())) {
        attachment.setLanguage(payload.getLanguage());
      }
      attachment.setCreationElement(
          StringUtils.isNotBlank(payload.getCreation())
              ? toUtcInstant(payload.getCreation())
              : new DateTimeType(Utils.getCurrentTimeStamp().getValueAsString()));
      communication.addPayload().setContent(attachment);
    }
  }

  /**
   * Converts an ISO-8601 timestamp with offset (ex: 2023-12-08T14:58:58.181+05:30) to the same
   * instant expressed in UTC. Utils.getFormattedDateTime would keep the local wall clock instead.
   */
  private DateTimeType toUtcInstant(String creation) {
    return new DateTimeType(OffsetDateTime.parse(creation).toInstant().toString());
  }

  private Meta buildMeta() throws ParseException {
    return new Meta()
        .setVersionId("1")
        .setLastUpdatedElement(Utils.getCurrentTimeStamp())
        .addProfile(ResourceProfileIdentifier.PROFILE_COMMUNICATION);
  }
}
