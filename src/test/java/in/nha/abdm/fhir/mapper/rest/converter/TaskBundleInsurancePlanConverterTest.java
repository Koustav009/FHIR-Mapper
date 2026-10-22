/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.helpers.OrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeBundleMetaResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeOrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeTaskResource;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.Test;

class TaskBundleInsurancePlanConverterTest {

  private final TaskBundleInsurancePlanConverter target =
      new TaskBundleInsurancePlanConverter(
          new MakeOrganisationResource(), new MakeTaskResource(), new MakeBundleMetaResource());

  private TaskBundleInsurancePlanRequest request() {
    return TaskBundleInsurancePlanRequest.builder()
        .bundleType("TaskBundle")
        .careContextReference("bc3c6c57-2053-4d0e-ac40-139ccccff645")
        .policyNumber("ABC123456789")
        .providerId("1234567890")
        .requester(
            OrganisationResource.builder()
                .facilityName("XYZ Hospital Co. Ltd.")
                .facilityId("1234567890")
                .build())
        .owner(
            OrganisationResource.builder()
                .facilityName("XYZ Insurance Co. Ltd.")
                .facilityId("4567878")
                .build())
        .build();
  }

  @Test
  void buildsTaskBundleWithExpectedEntriesAndProfiles() {
    Bundle bundle = target.makeTaskBundle(request());

    assertEquals(Bundle.BundleType.COLLECTION, bundle.getType());
    assertEquals(
        ResourceProfileIdentifier.PROFILE_TASK_BUNDLE,
        bundle.getMeta().getProfile().get(0).getValue());
    assertEquals("bc3c6c57-2053-4d0e-ac40-139ccccff645", bundle.getIdentifier().getValue());
    assertEquals(3, bundle.getEntry().size());

    Task task = assertInstanceOf(Task.class, bundle.getEntry().get(0).getResource());
    Organization owner =
        assertInstanceOf(Organization.class, bundle.getEntry().get(1).getResource());
    Organization requester =
        assertInstanceOf(Organization.class, bundle.getEntry().get(2).getResource());

    assertEquals("PRN", owner.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
    assertEquals("PRN", requester.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
    assertEquals("urn:uuid:" + owner.getId(), task.getOwner().getReference());
    assertEquals("urn:uuid:" + requester.getId(), task.getRequester().getReference());
    assertEquals("XYZ Insurance Co. Ltd.", task.getOwner().getDisplay());
    assertEquals("XYZ Hospital Co. Ltd.", task.getRequester().getDisplay());
  }
}
