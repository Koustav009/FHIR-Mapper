/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.services;

import in.nha.abdm.fhir.mapper.rest.common.constants.BundleFieldIdentifier;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.TaskInputResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Service;

@Service
public class TaskBundleExtractor extends NhcxExtractionSupport {

  private static final Set<String> KNOWN_INPUTS =
      Set.of(BundleFieldIdentifier.POLICY_NUMBER, BundleFieldIdentifier.PROVIDER_ID);

  public TaskBundleInsurancePlanRequest extract(Bundle bundle) {
    BundleResourceIndex index = new BundleResourceIndex(bundle);
    Task task = index.resources(Task.class).stream().findFirst().orElse(null);
    if (task == null) {
      return TaskBundleInsurancePlanRequest.builder()
          .careContextReference(bundleIdentifier(bundle))
          .build();
    }

    return TaskBundleInsurancePlanRequest.builder()
        .careContextReference(bundleIdentifier(bundle))
        .description(task.hasDescription() ? task.getDescription() : null)
        .requester(organization(index.resolve(task.getRequester(), Organization.class)))
        .owner(organization(index.resolve(task.getOwner(), Organization.class)))
        .policyNumber(inputValue(task, BundleFieldIdentifier.POLICY_NUMBER))
        .providerId(inputValue(task, BundleFieldIdentifier.PROVIDER_ID))
        .additionalInputs(additionalInputs(task))
        .build();
  }

  private String inputValue(Task task, String typeCode) {
    for (Task.ParameterComponent input : task.getInput()) {
      if (typeCode.equals(inputCode(input))) {
        Type value = input.getValue();
        return value == null ? null : value.primitiveValue();
      }
    }
    return null;
  }

  private List<TaskInputResource> additionalInputs(Task task) {
    List<TaskInputResource> inputs = new ArrayList<>();
    for (Task.ParameterComponent input : task.getInput()) {
      if (KNOWN_INPUTS.contains(inputCode(input))) {
        continue;
      }
      Type value = input.getValue();
      inputs.add(
          TaskInputResource.builder()
              .typeCode(inputCode(input))
              .typeDisplay(inputDisplay(input))
              .valueString(value == null ? null : value.primitiveValue())
              .build());
    }
    return inputs;
  }

  private String inputCode(Task.ParameterComponent input) {
    return input.hasType() && input.getType().hasCoding()
        ? input.getType().getCodingFirstRep().getCode()
        : null;
  }

  private String inputDisplay(Task.ParameterComponent input) {
    return input.hasType() && input.getType().hasCoding()
        ? input.getType().getCodingFirstRep().getDisplay()
        : null;
  }

  private String bundleIdentifier(Bundle bundle) {
    return bundle.hasIdentifier() ? bundle.getIdentifier().getValue() : null;
  }
}
