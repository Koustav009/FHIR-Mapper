/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.converter;

import in.nha.abdm.fhir.mapper.Utils;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.helpers.BundleUtils;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeBundleMetaResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeOrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeTaskResource;
import in.nha.abdm.fhir.mapper.rest.exceptions.ExceptionHandler;
import in.nha.abdm.fhir.mapper.rest.requests.TaskBundleInsurancePlanRequest;
import java.text.ParseException;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TaskBundleInsurancePlanConverter {
  private static final Logger log = LoggerFactory.getLogger(TaskBundleInsurancePlanConverter.class);

  private final MakeOrganisationResource makeOrganisationResource;
  private final MakeTaskResource makeTaskResource;
  private final MakeBundleMetaResource makeBundleMetaResource;

  public TaskBundleInsurancePlanConverter(
      MakeOrganisationResource makeOrganisationResource,
      MakeTaskResource makeTaskResource,
      MakeBundleMetaResource makeBundleMetaResource) {
    this.makeOrganisationResource = makeOrganisationResource;
    this.makeTaskResource = makeTaskResource;
    this.makeBundleMetaResource = makeBundleMetaResource;
  }

  public Bundle makeTaskBundle(TaskBundleInsurancePlanRequest request) {
    try {
      Organization requester = makeOrganisationResource.getOrganization(request.getRequester());
      Organization owner = makeOrganisationResource.getOrganization(request.getOwner());
      Task task = makeTaskResource.getTask(request, requester, owner);
      return buildBundle(request, task, owner, requester);
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, log);
    }
  }

  private Bundle buildBundle(
      TaskBundleInsurancePlanRequest request, Task task, Organization owner, Organization requester)
      throws ParseException {
    Bundle bundle = new Bundle();
    bundle.setId(UUID.randomUUID().toString());
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.setTimestampElement(Utils.getCurrentTimeStamp());
    bundle.setMeta(makeBundleMetaResource.getMeta(ResourceProfileIdentifier.PROFILE_TASK_BUNDLE));
    bundle.setIdentifier(new Identifier().setValue(request.getCareContextReference()));

    BundleUtils.addEntry(bundle, task);
    BundleUtils.addEntry(bundle, owner);
    BundleUtils.addEntry(bundle, requester);

    return bundle;
  }
}
