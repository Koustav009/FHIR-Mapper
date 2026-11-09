/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.converter;

import in.nha.abdm.fhir.mapper.Utils;
import in.nha.abdm.fhir.mapper.rest.common.constants.BundleUrlIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.helpers.BundleUtils;
import in.nha.abdm.fhir.mapper.rest.common.helpers.OrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeCoverageEligibilityRequestResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeCoverageResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakeOrganisationResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakePatientResource;
import in.nha.abdm.fhir.mapper.rest.dto.resources.MakePractitionerResource;
import in.nha.abdm.fhir.mapper.rest.exceptions.ExceptionHandler;
import in.nha.abdm.fhir.mapper.rest.requests.CoverageEligibilityRequestBundleRequest;
import java.text.ParseException;
import java.util.Objects;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.CoverageEligibilityRequest;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CoverageEligibilityRequestConverter {
  private static final Logger log =
      LoggerFactory.getLogger(CoverageEligibilityRequestConverter.class);

  private final MakePatientResource makePatientResource;
  private final MakePractitionerResource makePractitionerResource;
  private final MakeOrganisationResource makeOrganisationResource;
  private final MakeCoverageResource makeCoverageResource;
  private final MakeCoverageEligibilityRequestResource makeCoverageEligibilityRequestResource;

  public CoverageEligibilityRequestConverter(
      MakePatientResource makePatientResource,
      MakePractitionerResource makePractitionerResource,
      MakeOrganisationResource makeOrganisationResource,
      MakeCoverageResource makeCoverageResource,
      MakeCoverageEligibilityRequestResource makeCoverageEligibilityRequestResource) {
    this.makePatientResource = makePatientResource;
    this.makePractitionerResource = makePractitionerResource;
    this.makeOrganisationResource = makeOrganisationResource;
    this.makeCoverageResource = makeCoverageResource;
    this.makeCoverageEligibilityRequestResource = makeCoverageEligibilityRequestResource;
  }

  public Bundle makeCoverageEligibilityRequestBundle(
      CoverageEligibilityRequestBundleRequest request) {
    try {
      Patient patient = makePatientResource.getPatient(request.getPatient());
      if (request.getIsPmjay() != null && request.getIsPmjay())
        patient = updatePatientforPmjay(patient, request.getPatient().getPatientReference());
      Practitioner enterer = makePractitionerResource.getPractitioner(request.getEnterer());
      Organization provider = makeOrganisationResource.getOrganization(request.getProvider());
      provider.addType(buildTypeProv(request.getProvider()));
      provider.setIdentifier(null);
      provider.addIdentifier(buildIdentifierProv(request.getProvider()));
      Organization insurer = makeOrganisationResource.getOrganization(request.getInsurer());
      insurer.addType(buildTypePayer(request.getInsurer()));
      insurer.setIdentifier(null);
      insurer.addIdentifier(buildIdentifierPayer(request.getInsurer()));
      Location facility = createFacility(request, provider);
      Coverage coverage = makeCoverageResource.getCoverage(request.getCoverage(), patient, insurer);
      CoverageEligibilityRequest eligibilityRequest =
          makeCoverageEligibilityRequestResource.getRequest(
              request, patient, enterer, provider, insurer, facility, coverage);

      return buildBundle(
          request, eligibilityRequest, patient, enterer, provider, insurer, facility, coverage);
    } catch (Exception e) {
      throw ExceptionHandler.handle(e, log);
    }
  }

  private CodeableConcept buildTypeProv(OrganisationResource organisationResource) {
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setCode("prov")
                .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                .setDisplay("Healthcare Provider"));
  }

  private CodeableConcept buildTypePayer(OrganisationResource organisationResource) {
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setCode("pay")
                .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                .setDisplay("Payer"));
  }

  private Identifier buildIdentifierProv(OrganisationResource organisationResource) {
    Coding coding =
        new Coding()
            .setCode("NPI")
            .setSystem(ResourceProfileIdentifier.PROFILE_PROVIDER)
            .setDisplay("National provider identifier");

    String facilityId =
        Objects.nonNull(organisationResource) && organisationResource.getFacilityId() != null
            ? organisationResource.getFacilityId()
            : UUID.randomUUID().toString();

    return new Identifier()
        .setType(new CodeableConcept().addCoding(coding))
        .setSystem(BundleUrlIdentifier.NHCX_URL)
        .setValue(facilityId);
  }

  private Identifier buildIdentifierPayer(OrganisationResource organisationResource) {
    Coding coding =
        new Coding()
            .setCode("NIIP")
            .setSystem(ResourceProfileIdentifier.PROFILE_PROVIDER)
            .setDisplay("National Insurance Payor Identifier (Payor)");

    String facilityId =
        Objects.nonNull(organisationResource) && organisationResource.getFacilityId() != null
            ? organisationResource.getFacilityId()
            : UUID.randomUUID().toString();

    return new Identifier()
        .setType(new CodeableConcept().addCoding(coding))
        .setSystem(BundleUrlIdentifier.NHCX_URL)
        .setValue(facilityId);
  }

  private Patient updatePatientforPmjay(Patient patient, String patientId) {
    patient.setIdentifier(null);

    Identifier identifierPmj =
        new Identifier()
            .setType(
                new CodeableConcept()
                    .addCoding(
                        new Coding()
                            .setCode("PMJAY")
                            .setSystem(BundleUrlIdentifier.NDHM_IDENTIFIER_TYPE_CODE_SYSTEM)
                            .setDisplay("Pradhan Mantri Jan Aarogya Yojana (PMJAY) ID")))
            .setValue(patientId);
    Identifier identifier =
        new Identifier()
            .setType(
                new CodeableConcept()
                    .addCoding(
                        new Coding()
                            .setCode("MB")
                            .setSystem(ResourceProfileIdentifier.PROFILE_PROVIDER)
                            .setDisplay("Member Number")))
            .setValue(patientId);

    patient.addIdentifier(identifierPmj);
    patient.addIdentifier(identifier);

    return patient;
  }

  private Location createFacility(
      CoverageEligibilityRequestBundleRequest request, Organization provider) {
    Location location = new Location();
    location.setId(UUID.randomUUID().toString());
    location.setName(request.getFacility().getFacilityName());
    location.setManagingOrganization(Utils.buildReference(provider.getId()));
    Utils.setNarrative(location, "Facility: " + request.getFacility().getFacilityName());
    return location;
  }

  private Bundle buildBundle(
      CoverageEligibilityRequestBundleRequest request,
      CoverageEligibilityRequest eligibilityRequest,
      Patient patient,
      Practitioner enterer,
      Organization provider,
      Organization insurer,
      Location facility,
      Coverage coverage)
      throws ParseException {
    Bundle bundle = new Bundle();
    bundle.setId(UUID.randomUUID().toString());
    bundle.setType(Bundle.BundleType.COLLECTION);
    bundle.setTimestampElement(Utils.getCurrentTimeStamp());
    bundle.setMeta(buildBundleMeta());
    bundle.setIdentifier(
        new Identifier()
            .setSystem(BundleUrlIdentifier.WRAPPER_URL)
            .setValue(request.getCareContextReference()));

    BundleUtils.addEntry(bundle, eligibilityRequest);
    BundleUtils.addEntry(bundle, patient);
    BundleUtils.addEntry(bundle, enterer);
    BundleUtils.addEntry(bundle, provider);
    BundleUtils.addEntry(bundle, insurer);
    BundleUtils.addEntry(bundle, facility);
    BundleUtils.addEntry(bundle, coverage);

    return bundle;
  }

  private Meta buildBundleMeta() throws ParseException {
    return new Meta()
        .setVersionId("1")
        .setLastUpdatedElement(Utils.getCurrentTimeStamp())
        .addProfile(ResourceProfileIdentifier.PROFILE_COVERAGE_ELIGIBILITY_REQUEST_BUNDLE);
  }
}
