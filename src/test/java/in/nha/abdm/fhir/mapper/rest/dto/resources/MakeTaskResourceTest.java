/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.dto.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.TaskInputResource;
import java.util.List;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.junit.jupiter.api.Test;

class MakeTaskResourceTest {

  private final MakeTaskResource target = new MakeTaskResource();

  private Organization organization(String id, String name) {
    Organization organization = new Organization();
    organization.setId(id);
    organization.setName(name);
    return organization;
  }

  @Test
  void mapsCoreFieldsAndDefaults() throws Exception {
    TaskBundleInsurancePlanRequest request =
        TaskBundleInsurancePlanRequest.builder()
            .policyNumber("ABC123456789")
            .providerId("1234567890")
            .build();
    Organization requester = organization("hospital-1", "XYZ Hospital");
    Organization owner = organization("insurer-1", "XYZ Insurance");

    Task task = target.getTask(request, requester, owner);

    assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
    assertEquals(Task.TaskIntent.ORDER, task.getIntent());
    assertEquals("poll", task.getCode().getCodingFirstRep().getCode());
    assertEquals(
        "http://terminology.hl7.org/CodeSystem/financialtaskcode",
        task.getCode().getCodingFirstRep().getSystem());
    assertEquals(
        ResourceProfileIdentifier.PROFILE_TASK, task.getMeta().getProfile().get(0).getValue());
    assertEquals(2, task.getInput().size());
    assertEquals("policyNumber", task.getInput().get(0).getType().getCodingFirstRep().getCode());
    assertEquals("PolicyNumber", task.getInput().get(0).getType().getCodingFirstRep().getDisplay());
    assertEquals("ABC123456789", ((StringType) task.getInput().get(0).getValue()).getValue());
    assertEquals("providerId", task.getInput().get(1).getType().getCodingFirstRep().getCode());
    assertEquals("1234567890", ((StringType) task.getInput().get(1).getValue()).getValue());
    assertEquals("urn:uuid:hospital-1", task.getRequester().getReference());
    assertEquals("XYZ Hospital", task.getRequester().getDisplay());
    assertEquals("urn:uuid:insurer-1", task.getOwner().getReference());
    assertEquals("XYZ Insurance", task.getOwner().getDisplay());
  }

  @Test
  void honoursDescriptionAndAdditionalInputs() throws Exception {
    TaskBundleInsurancePlanRequest request =
        TaskBundleInsurancePlanRequest.builder()
            .policyNumber("ABC123456789")
            .providerId("1234567890")
            .description("Custom description")
            .additionalInputs(
                List.of(
                    TaskInputResource.builder()
                        .typeCode("claimId")
                        .typeDisplay("ClaimId")
                        .valueString("C-1")
                        .build()))
            .build();

    Task task =
        target.getTask(
            request, organization("hospital-1", "Hospital"), organization("insurer-1", "Insurer"));

    assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
    assertEquals("Custom description", task.getDescription());
    assertEquals(3, task.getInput().size());
    assertEquals("claimId", task.getInput().get(2).getType().getCodingFirstRep().getCode());
    assertEquals("C-1", ((StringType) task.getInput().get(2).getValue()).getValue());
  }
}
