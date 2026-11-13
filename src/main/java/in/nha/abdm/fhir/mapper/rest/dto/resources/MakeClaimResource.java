/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.dto.resources;

import in.nha.abdm.fhir.mapper.Utils;
import in.nha.abdm.fhir.mapper.rest.common.constants.BundleUrlIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.constants.ClaimSupportingInfoCategory;
import in.nha.abdm.fhir.mapper.rest.common.constants.ResourceProfileIdentifier;
import in.nha.abdm.fhir.mapper.rest.common.helpers.SupportingInfoResource;
import in.nha.abdm.fhir.mapper.rest.database.h2.services.SnomedService;
import in.nha.abdm.fhir.mapper.rest.database.h2.tables.SnomedConditionProcedure;
import in.nha.abdm.fhir.mapper.rest.requests.ClaimBundleRequest;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.ClaimItemResource;
import in.nha.abdm.fhir.mapper.rest.requests.helpers.ClaimProcedureResource;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Money;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MakeClaimResource {

  private final SnomedService snomedService;

  public Claim getClaim(
      ClaimBundleRequest request,
      Patient patient,
      List<Practitioner> practitioners,
      Organization provider,
      Organization insurer,
      Coverage coverage)
      throws ParseException {
    Claim claim = new Claim();
    claim.setId(UUID.randomUUID().toString());
    claim.setMeta(buildMeta());
    claim.addIdentifier(
        new Identifier()
            .setSystem(BundleUrlIdentifier.NHCX_URL)
            .setValue(request.getCareContextReference())
            .setType(
                new CodeableConcept()
                    .addCoding(
                        new Coding()
                            .setSystem(BundleUrlIdentifier.NDHM_IDENTIFIER_TYPE_CODE_SYSTEM)
                            .setCode("CLN")
                            .setDisplay("Claim number"))));
    claim.setStatus(resolveStatus(request.getStatus()));
    claim.setType(resolveType(request.getClaimType()));
    claim.setUse(resolveUse(request.getUse()));
    claim.setPatient(Utils.buildReference(patient.getId()));
    claim.setBillablePeriod(
        new Period()
            .setStart(Utils.toDate(request.getBillablePeriodStart()))
            .setEnd(Utils.toDate(request.getBillablePeriodEnd())));
    claim.setCreated(resolveCreated(request.getCreated()));
    claim.setProvider(Utils.buildReference(provider.getId()));
    claim.setInsurer(Utils.buildReference(insurer.getId()));
    claim.setPriority(resolvePriority(request.getPriority()));
    resolveCareTeam(claim, practitioners);
    resolveSupportingInfo(claim, request.getSupportingInfo());
    resolveProcedure(claim, request);

    claim
        .addInsurance()
        .setSequence(1)
        .setFocal(true)
        .setCoverage(Utils.buildReference(coverage.getId()));

    addDiagnoses(claim, request.getDiagnoses());
    double computedTotal = addItems(claim, request.getItems(), procedureType(request));
    double total = Objects.nonNull(request.getTotal()) ? request.getTotal() : computedTotal;
    claim.setTotal(money(total));

    Utils.setNarrative(claim, "Claim (" + claim.getUse().toCode() + ") to " + insurer.getName());
    return claim;
  }

  private void resolveProcedure(Claim claim, ClaimBundleRequest request) {
    if (Objects.isNull(request.getProcedures())) {
      return;
    }
    int sequence = 1;
    for (ClaimProcedureResource procedureRequest : request.getProcedures()) {
      String typeCode = procedureRequest.getProcedureType().toLowerCase();
      String typeDisplay = Character.toUpperCase(typeCode.charAt(0)) + typeCode.substring(1);
      Claim.ProcedureComponent procedure = claim.addProcedure();
      procedure.setId("Procedure/" + sequence);
      procedure
          .setSequence(sequence)
          .setType(
              List.of(
                  new CodeableConcept()
                      .addCoding(
                          new Coding()
                              .setSystem(BundleUrlIdentifier.NHCX_PROCEDURE_TYPE_SYSTEM)
                              .setCode(typeCode)
                              .setDisplay(typeDisplay))))
          .setProcedure(
              new Reference()
                  .setReference(BundleUrlIdentifier.NHCX_PROCEDURE_REFERENCE_BASE + sequence)
                  .setDisplay(procedureRequest.getProcedure()));
      if (Objects.nonNull(request.getBillablePeriodStart())) {
        procedure.setDate(Utils.toDate(request.getBillablePeriodStart()));
      }
      sequence++;
    }
  }

  private void resolveCareTeam(Claim claim, List<Practitioner> practitioners) {
    if (Objects.isNull(practitioners)) {
      return;
    }
    int sequence = 1;
    for (Practitioner practitioner : practitioners) {
      if (Objects.isNull(practitioner)) {
        continue;
      }
      boolean isPrimary = sequence == 1;
      claim
          .addCareTeam()
          .setSequence(sequence++)
          .setProvider(Utils.buildReference(practitioner.getId()))
          .setRole(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem("http://terminology.hl7.org/CodeSystem/claimcareteamrole")
                          .setCode(isPrimary ? "primary" : "assist")
                          .setDisplay(isPrimary ? "Primary provider" : "Assisting Provider")))
          .setQualification(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem(BundleUrlIdentifier.SNOMED_URL)
                          .setCode("394802001")
                          .setDisplay("General medicine")));
    }
  }

  private void resolveSupportingInfo(Claim claim, List<SupportingInfoResource> supportingInfo) {
    if (Objects.isNull(supportingInfo)) {
      return;
    }
    int sequence = 1;
    for (SupportingInfoResource info : supportingInfo) {
      if (Objects.isNull(info)) {
        continue;
      }
      Claim.SupportingInformationComponent supportingInfoComponent = claim.addSupportingInfo();
      supportingInfoComponent.setId("SupportingInformation/" + sequence);
      supportingInfoComponent
          .setSequence(sequence++)
          .setCategory(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem(
                              "http://terminology.hl7.org/CodeSystem/claim-information-category")
                          .setCode(info.getCategoryCode())
                          .setDisplay(
                              ClaimSupportingInfoCategory.getDisplay(info.getCategoryCode()))))
          .setCode(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem("http://terminology.hl7.org/CodeSystem/claim-information-code")
                          .setCode(info.getInformationCode())))
          .setValue(
              new Attachment()
                  .setTitle(info.getTitle())
                  .setContentType(info.getContentType())
                  .setData(Base64.getDecoder().decode(info.getData())));
    }
  }

  private void addDiagnoses(Claim claim, List<String> diagnoses) {
    if (Objects.isNull(diagnoses)) {
      return;
    }
    int sequence = 1;
    for (String diagnosis : diagnoses) {
      if (diagnosis == null || diagnosis.isBlank()) {
        continue;
      }
      claim
          .addDiagnosis()
          .setSequence(sequence++)
          .setDiagnosis(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem("http://hl7.org/fhir/sid/icd-10")
                          .setCode(diagnosis)
                          .setDisplay(diagnosis)))
          .addType(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem(ResourceProfileIdentifier.DIAGNOSIS_TYPE_SYSTEM)
                          .setCode("principal")
                          .setDisplay("Principal Diagnosis")))
          .setOnAdmission(
              new CodeableConcept()
                  .addCoding(
                      new Coding()
                          .setSystem(
                              "http://terminology.hl7.org/CodeSystem/ex-diagnosis-on-admission")
                          .setCode("yes")
                          .setDisplay("Yes")));
    }
  }

  private double addItems(Claim claim, List<ClaimItemResource> items, String procedureType) {
    List<PositiveIntType> careTeamSequences = sequenceList(claim.getCareTeam().size());
    List<PositiveIntType> diagnosisSequences = sequenceList(claim.getDiagnosis().size());
    List<PositiveIntType> procedureSequences = sequenceList(claim.getProcedure().size());
    List<PositiveIntType> informationSequences = sequenceList(claim.getSupportingInfo().size());
    double runningTotal = 0.0;
    int sequence = 1;
    for (ClaimItemResource item : items) {
      int quantity = Objects.nonNull(item.getQuantity()) ? item.getQuantity() : 1;
      double net = Objects.nonNull(item.getNet()) ? item.getNet() : item.getUnitPrice() * quantity;
      runningTotal += net;
      Claim.ItemComponent itemComponent = claim.addItem();
      itemComponent.setId("Item/" + sequence);
      itemComponent
          .setSequence(sequence)
          .setCategory(benefitCategory(procedureType))
          .setProductOrService(productOrService(item))
          .setQuantity(new SimpleQuantity().setValue(quantity))
          .setUnitPrice(money(item.getUnitPrice()))
          .setNet(money(net));
      if (!careTeamSequences.isEmpty()) {
        itemComponent.setCareTeamSequence(careTeamSequences);
      }
      if (!diagnosisSequences.isEmpty()) {
        itemComponent.setDiagnosisSequence(diagnosisSequences);
      }
      if (!procedureSequences.isEmpty()) {
        itemComponent.setProcedureSequence(procedureSequences);
      }
      if (!informationSequences.isEmpty()) {
        itemComponent.setInformationSequence(informationSequences);
      }
      if (Objects.nonNull(item.getServicedPeriodStart())
          || Objects.nonNull(item.getServicedPeriodEnd())) {
        itemComponent.setServiced(
            new Period()
                .setStart(Utils.toDate(item.getServicedPeriodStart()))
                .setEnd(Utils.toDate(item.getServicedPeriodEnd())));
      }
      if (Objects.nonNull(item.getFactor())) {
        itemComponent.setFactor(item.getFactor());
      }
      addModifier(itemComponent, item);
      addProgramCode(itemComponent, item);
      sequence++;
    }
    return runningTotal;
  }

  private String procedureType(ClaimBundleRequest request) {
    if (Objects.isNull(request.getProcedures()) || request.getProcedures().isEmpty()) {
      return null;
    }
    return request.getProcedures().get(0).getProcedureType();
  }

  private CodeableConcept benefitCategory(String procedureType) {
    if (Objects.isNull(procedureType)) {
      return null;
    }
    String typeDisplay =
        Character.toUpperCase(procedureType.charAt(0)) + procedureType.substring(1);
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setSystem(ResourceProfileIdentifier.CLAIM_ITEM_BENEFIT_CATEGORY_SYSTEM)
                .setCode(typeDisplay)
                .setDisplay(typeDisplay));
  }

  private CodeableConcept productOrService(ClaimItemResource item) {
    CodeableConcept productOrService = new CodeableConcept().setText(item.getService());
    if (Objects.nonNull(item.getCode()) && !item.getCode().isBlank()) {
      productOrService.addCoding(
          new Coding()
              .setSystem(ResourceProfileIdentifier.CLAIM_ITEM_PROCEDURE_CODE_SYSTEM)
              .setCode(item.getCode())
              .setDisplay(item.getService()));
    }
    return productOrService;
  }

  private List<PositiveIntType> sequenceList(int count) {
    List<PositiveIntType> sequences = new ArrayList<>(count);
    for (int i = 1; i <= count; i++) {
      sequences.add(new PositiveIntType(i));
    }
    return sequences;
  }

  private void addModifier(Claim.ItemComponent itemComponent, ClaimItemResource item) {
    if (Objects.isNull(item.getModifier()) || item.getModifier().isBlank()) {
      return;
    }
    Coding coding = new Coding().setCode(item.getModifier());
    if (Objects.nonNull(item.getModifierDisplay()) && !item.getModifierDisplay().isBlank()) {
      coding.setDisplay(item.getModifierDisplay());
    }
    itemComponent.addModifier(new CodeableConcept().addCoding(coding));
  }

  private void addProgramCode(Claim.ItemComponent itemComponent, ClaimItemResource item) {
    if (Objects.isNull(item.getProgramCode()) || item.getProgramCode().isBlank()) {
      return;
    }
    String display =
        "AB-PMJAY".equals(item.getProgramCode())
            ? "Ayushman Bharat Pradhan Mantri Jan Arogya Yojana (AB-PMJAY)"
            : item.getProgramCode();
    itemComponent
        .addProgramCode()
        .addCoding(
            new Coding()
                .setSystem(ResourceProfileIdentifier.CLAIM_ITEM_PROGRAM_CODE_SYSTEM)
                .setCode(item.getProgramCode())
                .setDisplay(display));
  }

  private Money money(double value) {
    return new Money().setValue(value).setCurrency(ResourceProfileIdentifier.CURRENCY_INR);
  }

  private Claim.ClaimStatus resolveStatus(String status) {
    if (Objects.isNull(status) || status.isBlank()) {
      return Claim.ClaimStatus.ACTIVE;
    }
    return Claim.ClaimStatus.fromCode(status);
  }

  private Claim.Use resolveUse(String use) {
    if (Objects.isNull(use) || use.isBlank()) {
      return Claim.Use.CLAIM;
    }
    return Claim.Use.fromCode(use);
  }

  private CodeableConcept resolveType(String claimType) {
    String resolvedType =
        (Objects.isNull(claimType) || claimType.isBlank())
            ? "Inpatient care management (procedure)"
            : claimType;
    SnomedConditionProcedure snomedConditionProcedure =
        snomedService.getConditionProcedureCode(resolvedType);
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setSystem(BundleUrlIdentifier.SNOMED_URL)
                .setCode(snomedConditionProcedure.getCode())
                .setDisplay(snomedConditionProcedure.getDisplay()));
  }

  private CodeableConcept resolvePriority(String priority) {
    String code = (Objects.isNull(priority) || priority.isBlank()) ? "normal" : priority;
    return new CodeableConcept()
        .addCoding(
            new Coding()
                .setSystem(ResourceProfileIdentifier.PROCESS_PRIORITY_SYSTEM)
                .setCode(code));
  }

  private Date resolveCreated(String created) {
    if (Objects.isNull(created) || created.isBlank()) {
      return new Date();
    }
    return Utils.getFormattedDate(created);
  }

  private Meta buildMeta() throws ParseException {
    return new Meta()
        .setVersionId("1")
        .setLastUpdatedElement(Utils.getCurrentTimeStamp())
        .addProfile(ResourceProfileIdentifier.PROFILE_CLAIM);
  }
}
