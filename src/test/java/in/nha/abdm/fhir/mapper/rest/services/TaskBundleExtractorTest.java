/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.nha.abdm.fhir.mapper.rest.common.constants.ValidationConstants;
import in.nha.abdm.fhir.mapper.rest.common.helpers.OrganisationResource;
import in.nha.abdm.fhir.mapper.rest.converter.TaskBundleInsurancePlanConverter;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeBundleMetaResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeOrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeTaskResource;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.TaskInputResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskBundleExtractorTest {

  private final TaskBundleExtractor extractor = new TaskBundleExtractor();
  private final TaskBundleInsurancePlanConverter converter =
      new TaskBundleInsurancePlanConverter(
          new MakeOrganisationResource(), new MakeTaskResource(), new MakeBundleMetaResource());

  @Test
  void roundTripsTaskBundleRequest() {
    TaskBundleInsurancePlanRequest request = request();

    TaskBundleInsurancePlanRequest extracted = extractor.extract(converter.makeTaskBundle(request));

    assertEquals(request.getCareContextReference(), extracted.getCareContextReference());
    assertEquals(request.getDescription(), extracted.getDescription());
    assertEquals(request.getPolicyNumber(), extracted.getPolicyNumber());
    assertEquals(request.getProviderId(), extracted.getProviderId());
    assertEquals(request.getRequester(), extracted.getRequester());
    assertEquals(request.getOwner(), extracted.getOwner());
    assertEquals(request.getAdditionalInputs(), extracted.getAdditionalInputs());
  }

  private TaskBundleInsurancePlanRequest request() {
    return TaskBundleInsurancePlanRequest.builder()
        .bundleType(ValidationConstants.TASK_BUNDLE)
        .careContextReference("bc3c6c57-2053-4d0e-ac40-139ccccff645")
        .description("Fetch insurance plan")
        .requester(
            OrganisationResource.builder()
                .facilityName("City Hospital")
                .facilityId("HOSP-1")
                .build())
        .owner(
            OrganisationResource.builder().facilityName("Star Insurer").facilityId("INS-1").build())
        .policyNumber("ABC123456789")
        .providerId("1234567890")
        .additionalInputs(
            List.of(
                TaskInputResource.builder()
                    .typeCode("memberId")
                    .typeDisplay("MemberId")
                    .valueString("M-1")
                    .build()))
        .build();
  }
}
