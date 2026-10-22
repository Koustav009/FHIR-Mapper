/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.dto.resources;

import in.nha.abdm.fhir.mapper.Utils;
import in.nha.abdm.fhir.mapper.rest.common.constants.BundleFieldIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.TaskInputResource;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.springframework.stereotype.Component;

@Component
public class MakeTaskResource {

  public Task getTask(
      TaskBundleInsurancePlanRequest request, Organization requester, Organization owner)
      throws ParseException {
    Task task = new Task();
    task.setId(UUID.randomUUID().toString());
    task.setMeta(buildMeta());
    task.setStatus(Task.TaskStatus.COMPLETED);
    task.setIntent(Task.TaskIntent.ORDER);
    task.setCode(buildCode());
    task.setDescription(resolveDescription(request.getDescription()));
    task.setAuthoredOnElement(new DateTimeType(new Date()));
    task.setRequester(buildReference(requester));
    task.setOwner(buildReference(owner));

    addInput(
        task,
        BundleFieldIdentifier.POLICY_NUMBER,
        BundleFieldIdentifier.POLICY_NUMBER_DISPLAY,
        request.getPolicyNumber());
    addInput(
        task,
        BundleFieldIdentifier.PROVIDER_ID,
        BundleFieldIdentifier.PROVIDER_ID_DISPLAY,
        request.getProviderId());
    addAdditionalInputs(task, request.getAdditionalInputs());

    Utils.setNarrative(task, "Task: " + task.getDescription());
    return task;
  }

  private Reference buildReference(Organization organization) {
    return Utils.buildReference(organization.getId()).setDisplay(organization.getName());
  }

  private CodeableConcept buildCode() {
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setSystem(ResourceProfileIdentifier.FINANCIAL_TASK_CODE_SYSTEM)
                .setCode(BundleFieldIdentifier.TASK_POLL)
                .setDisplay(BundleFieldIdentifier.TASK_POLL_DISPLAY));
  }

  private void addAdditionalInputs(Task task, List<TaskInputResource> additionalInputs) {
    if (additionalInputs == null) {
      return;
    }
    for (TaskInputResource input : additionalInputs) {
      addInput(task, input.getTypeCode(), input.getTypeDisplay(), input.getValueString());
    }
  }

  private void addInput(Task task, String typeCode, String typeDisplay, String value) {
    task.addInput()
        .setType(
            new CodeableConcept()
                .addCoding(
                    new Coding()
                        .setSystem(ResourceProfileIdentifier.TASK_INPUT_TYPE_CODE_SYSTEM)
                        .setCode(typeCode)
                        .setDisplay(typeDisplay)))
        .setValue(new StringType(value));
  }

  private String resolveDescription(String description) {
    return Objects.nonNull(description) && !description.isBlank()
        ? description
        : BundleFieldIdentifier.TASK_DESCRIPTION;
  }

  private Meta buildMeta() throws ParseException {
    return new Meta()
        .setVersionId("1")
        .setLastUpdatedElement(Utils.getCurrentTimeStamp())
        .addProfile(ResourceProfileIdentifier.PROFILE_TASK);
  }
}
