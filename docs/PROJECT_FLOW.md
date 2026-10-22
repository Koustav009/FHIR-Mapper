# FHIR Mapper — Project Flow Documentation

**Project**: ABDM (Ayushman Bharat Digital Mission) FHIR Mapper  
**Base Package**: `in.nha.abdm.fhir.mapper`  
**Port**: `8085`  
**FHIR Version**: R4 (via HAPI FHIR 7.4.0)  
**DB**: H2 in-memory (`jdbc:h2:mem:fhir_mapper`)  
**Stack**: Spring Boot 3.2.4, Spring Data JPA, HAPI FHIR, Lombok

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Complete Request Flow (End-to-End)](#2-complete-request-flow-end-to-end)
3. [Bundle Types & Converters](#3-bundle-types--converters)
4. [Resource Builder Components](#4-resource-builder-components)
5. [Validation Pipeline](#5-validation-pipeline)
6. [Exception Handling](#6-exception-handling)
7. [SNOMED CT Code Resolution](#7-snomed-ct-code-resolution)
8. [Library / Auto-Configuration Mode](#8-library--auto-configuration-mode)
9. [All REST Endpoints](#9-all-rest-endpoints)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client (HIS / HMIS)                         │
│              POST /v1/bundle/{type}  (application/json)            │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        BundleController                              │
│                                                                      │
│  1. Receives @Valid @RequestBody XxxRequest                          │
│  2. Jakarta Bean Validation (@NotBlank, @Pattern, @Valid)            │
│  3. Custom @NotBlankFields validation on nested objects              │
│  4. Delegates to appropriate Converter                               │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        Converter (e.g. OPConsultationConverter)      │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Phase 1: Decompose Request → FHIR Resources                 │   │
│  │                                                               │   │
│  │  MakePatientResource        → Patient                         │   │
│  │  MakePractitionerResource   → Practitioner[]                  │   │
│  │  MakeOrganisationResource   → Organization                    │   │
│  │  MakeEncounterResource      → Encounter                       │   │
│  │  MakeConditionResource      → Condition[]        ──┐          │   │
│  │  MakeObservationResource    → Observation[]        │          │   │
│  │  MakeAllergyToleranceResource → AllergyIntolerance │  Uses    │   │
│  │  MakeMedicationRequestResource → MedicationRequest│ Snomed   │   │
│  │  MakeProcedureResource      → Procedure[]         │ Service  │   │
│  │  MakeServiceRequestResource → ServiceRequest[]    ──┘          │   │
│  │  MakeDocumentResource       → DocumentReference[]              │   │
│  │                                                               │   │
│  │  Each Make* component calls SnomedService to resolve           │   │
│  │  free-text display names → SNOMED CT codes via fuzzy match    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Phase 2: Build Composition                                  │   │
│  │                                                               │   │
│  │  MakeXxxComposition → Composition resource                    │   │
│  │  (acts as a table of contents for the DOCUMENT bundle)        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Phase 3: Assemble Bundle                                    │   │
│  │                                                               │   │
│  │  Bundle type: DOCUMENT                                        │   │
│  │  BundleUtils.addEntry() / addEntries() assigns urn:uuid: IDs  │   │
│  │  Bundle.id = UUID                                             │   │
│  │  Bundle.timestamp = now                                       │   │
│  │  Bundle.identifier = careContextReference                     │   │
│  │  Bundle.meta.profile = ABDM profile URL                       │   │
│  └──────────────────────────────────────────────────────────────┘   │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│              validateAndReturnBundle() (Controller)                   │
│                                                                      │
│  if fhir.validation.enabled=true:                                    │
│    FhirValidationService.validateBundle(bundle)                      │
│      └─ HAPI FHIR FhirValidator.validateWithResult()                 │
│         validates against base R4 + ABDM NPM profiles                │
│                                                                      │
│    if invalid AND fhir.validation.fail-on-error=true:                │
│      → throw FhirValidationException (HTTP 400)                      │
│    else:                                                             │
│      → log warnings, continue                                        │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│              FhirHttpMessageConverter                                │
│                                                                      │
│  fhirContext.newJsonParser().encodeResourceToString(bundle)          │
│  → Writes to response body as application/fhir+json                  │
└───────────────────────────┬──────────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────────┐
│                    HTTP 201 Created                                  │
│  Content-Type: application/fhir+json                                 │
│  Body: FHIR R4 Bundle (DOCUMENT type)                                │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Complete Request Flow (End-to-End)

### Step-by-Step

**Phase 0 — Startup / Data Loading** (`DbLoader` at `@PostConstruct`)

1. `PathMatchingResourcePatternResolver` scans `classpath:/snomed/*.json`
2. For each JSON file, the filename (minus `.json`) is mapped to an entity name (e.g. `SnomedMedicine.json` → `SnomedMedicine`)
3. The matching Spring Data JPA repository is looked up by bean name (`snomedMedicineRepo`)
4. Jackson `ObjectMapper` deserializes the JSON array into `List<Entity>` using a `TypeReference` map
5. `repository.saveAll(entities)` inserts all records into H2 within a single transaction
6. A composite index `idx_{table}_display_code` is created on `(code, display)` for fast lookups

**Phase 1 — HTTP Request**

- Client sends `POST /v1/bundle/{type}` with `Content-Type: application/json`
- Body is one of 8 request DTOs (e.g. `OPConsultationRequest`)
- All DTOs carry common fields: `bundleType`, `careContextReference`, `patient`, plus type-specific fields

**Phase 2 — Controller Layer** (`BundleController`)

- `@Valid` triggers Jakarta Bean Validation on the request DTO
- `@NotBlankFields` (custom annotation) validates nested lists aren't empty where required
- Controller calls the appropriate converter (e.g. `opConsultationConverter.convertToOPConsultationBundle(request)`)

**Phase 3 — Converter Layer** (e.g. `OPConsultationConverter`)

- The converter orchestrates creation of every FHIR resource type needed for that clinical record
- Each `Make*Resource` component receives sub-sections of the request and returns a complete FHIR resource
- `Make*Resource` components inject `SnomedService` to resolve free-text clinical terms into SNOMED CT codes via fuzzy matching
- All resources are assembled into a `Composition` resource (the document index)
- All resources + composition are packed into a `Bundle` of type `DOCUMENT`

**Phase 4 — Validation** (Optional, in the Controller)

- If `fhir.validation.enabled=true`, the bundle is validated using HAPI FHIR's `FhirValidator` against base R4 profiles
- If validation fails and `fhir.validation.fail-on-error=true`, a `FhirValidationException` is thrown

**Phase 5 — Serialization**

- Spring's `FhirHttpMessageConverter` uses HAPI FHIR's JSON parser to serialize the Bundle
- Response is `HTTP 201 Created` with `Content-Type: application/fhir+json`

---

## 3. Bundle Types & Converters

| # | Endpoint | Converter | Request DTO | ABDM Record Type |
|---|----------|-----------|-------------|------------------|
| 1 | `POST /v1/bundle/immunization` | `ImmunizationConverter` | `ImmunizationRequest` | ImmunizationRecord |
| 2 | `POST /v1/bundle/prescription` | `PrescriptionConverter` | `PrescriptionRequest` | PrescriptionRecord |
| 3 | `POST /v1/bundle/op-consultation` | `OPConsultationConverter` | `OPConsultationRequest` | OPConsultRecord |
| 4 | `POST /v1/bundle/health-document` | `HealthDocumentConverter` | `HealthDocumentRecord` | HealthDocumentRecord |
| 5 | `POST /v1/bundle/diagnostic-report` | `DiagnosticReportConverter` | `DiagnosticReportRequest` | DiagnosticReportRecord |
| 6 | `POST /v1/bundle/discharge-summary` | `DischargeSummaryConverter` | `DischargeSummaryRequest` | DischargeSummaryRecord |
| 7 | `POST /v1/bundle/wellness-record` | `WellnessRecordConverter` | `WellnessRecordRequest` | WellnessRecord |
| 8 | `POST /v1/bundle/invoice` | `InvoiceRequestConverter` | `InvoiceBundleRequest` | Invoice |

Every converter follows the same 3-phase pipeline: **decompose → compose → bundle**.

### What Each Converter Produces (FHIR Resources)

| FHIR Resource | Immunization | Prescription | OP Consult | Diagnostic | Discharge | Wellness | Invoice |
|---------------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Patient | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Practitioner(s) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Organization | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Encounter | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Condition(s) | | | ✓ | | ✓ | | |
| Observation(s) | | | ✓ | | ✓ | ✓ | |
| AllergyIntolerance | | | ✓ | | ✓ | | |
| FamilyMemberHistory | | | ✓ | | ✓ | | |
| ServiceRequest | | | ✓ | | | | |
| MedicationRequest(s) | | ✓ | ✓ | | ✓ | | |
| Procedure(s) | | | ✓ | | ✓ | | |
| DocumentReference(s) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
| DiagnosticReport | | | | ✓ | ✓ | | |
| Immunization | ✓ | | | | | | |
| CarePlan | | | | | ✓ | | |
| Appointment | | | ✓ | | | | |
| Invoice / ChargeItem | | | | | | | ✓ |
| Composition | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

---

## 4. Resource Builder Components

All located in `src/main/java/in/nha/abdm/fhir/mapper/rest/dto/resources/`.

Each `Make*Resource` component is a Spring `@Component` that:

1. Accepts request sub-objects + references to related resources (Patient, Practitioner, etc.)
2. Calls `SnomedService` to resolve free-text clinical terms into coded SNOMED CT concepts
3. Constructs and returns a complete FHIR R4 resource with:
   - UUID as resource ID
   - Appropriate meta/profiles
   - Required references to other resources (subject, requester, etc.)
   - Human-readable `narrative` (via `Utils.setNarrative()`)

### Which Components Use SnomedService

| Component | SnomedService Call(s) |
|-----------|-----------------------|
| `MakeConditionResource` | `getConditionProcedureCode(display)` |
| `MakeObservationResource` | `getSnomedObservationCode(display)` |
| `MakeWellnessObservationResource` | `getSnomedObservationCode(display)` |
| `MakeEncounterResource` | `getSnomedEncounterCode(display)` |
| `MakeMedicationRequestResource` | `getSnomedMedicineCode(medicine)` + `getSnomedMedicineRouteCode(route)` |
| `MakeProcedureResource` | `getConditionProcedureCode(display)` |
| `MakeAllergyToleranceResource` | `getConditionProcedureCode(display)` |
| `MakeServiceRequestResource` | `getSnomedDiagnosticCode(display)` |
| `MakeDiagnosticLabResource` | `getSnomedDiagnosticCode(display)` + `getSnomedObservationCode(display)` |
| `MakeFamilyMemberResource` | `getConditionProcedureCode(display)` |
| `MakeImmunizationResource` | `getSnomedVaccineCode(display)` |
| `MakeCarePlanResource` | `getSnomedObservationCode(display)` |
| `MakeInvoiceMedicationResource` | `getSnomedMedicineCode(display)` |

---

## 5. Validation Pipeline

Controlled by `application.properties`:

```properties
fhir.validation.enabled=true
fhir.validation.fail-on-error=true
fhir.validation.log-details=true
```

**`FhirValidationService`** wraps HAPI FHIR's `FhirValidator`:

1. Creates a `FhirInstanceValidator` with the project's `FhirContext` (R4)
2. Configures validation options:
   - `ALLOW_UNKNOWN_DOMAINS` = false (unknown code systems error out)
   - `ALLOW_ANY_EXTENSIONS` (depends on config)
3. Calls `validator.validateWithResult(bundle)` to get a `ValidationResult`
3. Maps HAPI FHIR's `SingleValidationMessage` list into the project's `ValidationResult` DTO with: severity, code, details, location, expression

**In the Controller** (`validateAndReturnBundle`):

- If validation disabled → skip, return bundle as-is
- If validation enabled but failed:
  - `failOnValidationError=true` → throw `FhirValidationException` (HTTP 400)
  - `failOnValidationError=false` → log warnings, return bundle anyway

---

## 6. Exception Handling

`GlobalExceptionHandler` (`@ControllerAdvice`) catches:

| Exception | HTTP Status | Response Body |
|-----------|:-----------:|---------------|
| `FhirMapperException` | `400` | `FacadeError { ErrorResponse, ValidationErrorResponse }` |
| `MethodArgumentNotValidException` | `400` | `ValidationErrorResponse { fieldErrors[] }` |
| `HttpMessageNotReadableException` | `400` | Parse error detail |
| `ParseException` | `400` | `ErrorResponse` |
| `FhirValidationException` | `400` | `ValidationResult` |
| `DataFormatException` | `400` | `FacadeError` |

---

## 7. SNOMED CT Code Resolution

This is the core mechanism that converts free-text clinical terms (e.g. "Fever", "Aspirin 75 mg") into standard SNOMED CT codes for the FHIR output.

### 7.1 Startup: JSON → H2 Database

**File**: `src/main/java/in/nha/abdm/fhir/mapper/rest/database/h2/config/DbLoader.java`

At application startup (`@PostConstruct`), `DbLoader.loadData()` runs:

```
1. Scan classpath: /snomed/*.json
   ─────────────────────────────────────────────
   SnomedMedicine.json           → snomed_medicine table
   SnomedConditionProcedure.json → snomed_condition_procedure table
   SnomedEncounter.json          → snomed_encounter table
   SnomedSpecimen.json           → snomed_specimen table
   SnomedObservation.json        → snomed_observation table
   SnomedVaccine.json            → snomed_vaccine table
   SnomedDiagnostic.json         → snomed_diagnostic table
   SnomedMedicineRoute.json      → snomed_medicine_route table
   TypeChargeItem.json           → type_charge_item table
   TypeInvoice.json              → type_invoice table

2. For each file:
   a. Derive entity name from filename (strip .json)
   b. Derive repository bean name: lowercase first char + "Repo"
      e.g. "SnomedMedicine" → "snomedMedicineRepo"
   c. Look up JpaRepository from Spring ApplicationContext
   d. Jackson deserializes JSON array → List<Entity>
   e. repository.saveAll(entities) — bulk insert
   f. CREATE INDEX idx_{table}_display_code ON "{table}" ("code", "display")

   All wrapped in a single PlatformTransactionManager transaction.
```

**JSON Format** (all files follow this pattern):

```json
[
  { "code": "319775004", "display": "Aspirin 75 mg oral tablet" },
  { "code": "26643006",  "display": "Oral route" },
  { "code": "119297000", "display": "Blood specimen" }
]
```

**Entity Structure** (all 8 SNOMED entities share this):

| Field | Type | Role |
|-------|------|------|
| `code` | `String` (PK) | SNOMED CT concept ID (e.g. `319775004`) |
| `display` | `String` | Human-readable name (e.g. "Aspirin 75 mg oral tablet") |
| `type` | `String` | Type discriminator (e.g. "Condition/Procedure", "Medications") |

All entities implement the `Displayable` interface, which requires `getDisplay()`.

**Repository Pattern** (all 8 are identical):

```java
@Repository
public interface SnomedConditionProcedureRepo extends JpaRepository<SnomedConditionProcedure, String> {
    List<SnomedConditionProcedure> findTop20ByDisplayContainingIgnoreCase(String display);
}
```

The query method `findTop20ByDisplayContainingIgnoreCase` returns up to 20 rows where the `display` column contains the input string (case-insensitive). This is the **Phase A** database filter.

### 7.2 Runtime: Fuzzy Matching Lookup

**File**: `src/main/java/in/nha/abdm/fhir/mapper/rest/database/h2/services/SnomedService.java`

When a resource builder needs to code a clinical term, the flow is:

```
MakeConditionResource.getCondition("Fever", patient)
    │
    ▼
snomedService.getConditionProcedureCode("Fever")
    │
    ├── @Cacheable("snomed_condition_procedure", key="Fever")
    │   (cached result returned if same display was looked up before)
    │
    ├── Phase A: Database Query
    │   snomedConditionProcedureRepo.findTop20ByDisplayContainingIgnoreCase("Fever")
    │   → Returns up to 20 candidates where display contains "Fever"
    │     e.g. ["Fever", "Fever with chills", "Typhoid fever", ...]
    │
    ├── Phase B: Fuzzy Matching (SnomedService.fuzzyMatch())
    │   │
    │   ├── Step 1: Filter valid items (filterValidItems)
    │   │   hasValidWordDifference("Fever", candidateDisplay)
    │   │   → Rejects candidates whose word count > input word count + 2
    │   │   e.g. input="Fever" (1 word) → allows 1-3 word candidates
    │   │   Rejects: "Typhoid fever with complications and sequelae" (5 words)
    │   │
    │   ├── Step 2: Cosine Similarity scoring
    │   │   calculateSimilarity("Fever", candidateDisplay)
    │   │   │
    │   │   ├── Tokenize both strings into words (lowercase)
    │   │   ├── Build frequency maps: {"fever": 1} vs {"typhoid":1, "fever":1}
    │   │   └── CosineSimilarity.cosineSimilarity(map1, map2)
    │   │       → Returns 0.0 to 1.0 score
    │   │
    │   └── Step 3: Pick max score
    │       .max(Map.Entry.comparingByValue())
    │       → Returns the entity with the highest similarity score
    │
    └── Phase C: Fallback
        if fuzzyMatch returned null → use SNOMED_UNKNOWN code "261665006"
        if fuzzyMatch returned match → use its code and display
```

### 7.3 How the Coded Result Is Used

In `MakeConditionResource.createCode()`:

```java
SnomedConditionProcedure snomed = snomedService.getConditionProcedureCode(conditionDetails);
return new CodeableConcept()
    .addCoding(new Coding()
        .setDisplay(snomed.getDisplay())
        .setCode(snomed.getCode())
        .setSystem("http://snomed.info/sct"))
    .setText(conditionDetails);  // preserves the original input as text
```

The FHIR resource ends up with a `CodeableConcept` containing:
- **`text`** = the original free-text input from the client (e.g. "Fever")
- **`coding.code`** = the best-matched SNOMED CT code (e.g. `"27464006"`)
- **`coding.display`** = the matched display text (e.g. "Fever with chills")
- **`coding.system`** = `"http://snomed.info/sct"`

### 7.4 Default / Fallback Codes

| Scenario | SNOMED Code | Defined In |
|----------|-------------|------------|
| No fuzzy match found | `261665006` (SNOMED_UNKNOWN) | `SnomedCodeIdentifier` |
| Encounter display is null | `AMB` (Ambulatory) | `SnomedCodeIdentifier.SNOMED_ENCOUNTER_AMBULATORY` |

### 7.5 REST API for SNOMED Reference

`SnomedController` provides read-only access to all loaded codes:

```
GET /v1/snomed/{resource}
```

Where `{resource}` is one of: `Condition`, `Procedure`, `Encounter`, `Medication-Route`, `Medications`, `Observations`, `Specimen`, `Vaccines`.

Returns a `SnomedResponse` with the full list of codes for that category and the `availableSnomed` list of valid resource names.

### 7.6 Caching

All `get*Code()` methods are annotated with `@Cacheable`, keyed by `#display`, with separate cache regions per SNOMED type:

| Cache Region | Method |
|--------------|--------|
| `snomed_condition_procedure` | `getConditionProcedureCode()` |
| `snomed_diagnostic` | `getSnomedDiagnosticCode()` |
| `snomed_encounter` | `getSnomedEncounterCode()` |
| `snomed_medicine` | `getSnomedMedicineCode()` |
| `snomed_observation` | `getSnomedObservationCode()` |
| `snomed_specimen` | `getSnomedSpecimenCode()` |
| `snomed_vaccine` | `getSnomedVaccineCode()` |
| `snomed_route` | `getSnomedMedicineRouteCode()` |

Caching is enabled by `@EnableCaching` on `FHIRApplication`.

### 7.7 Visual Summary: SNOMED Code Lookup

```
                    Free-text input
                    (e.g. "Fever")
                         │
                         ▼
     ┌───────────────────────────────────────┐
     │     @Cacheable check                  │
     │  (return cached if same display)      │
     └──────────────┬────────────────────────┘
                    │ (cache miss)
                    ▼
     ┌───────────────────────────────────────┐
     │  H2 Query: findTop20ByDisplayContaining│
     │           IgnoreCase("Fever")          │
     └──────────────┬────────────────────────┘
                    │ (up to 20 candidates)
                    ▼
     ┌───────────────────────────────────────┐
     │  Word Count Filter                     │
     │  (candidate.words ≤ input.words + 2)  │
     └──────────────┬────────────────────────┘
                    │ (filtered list)
                    ▼
     ┌───────────────────────────────────────┐
     │  Cosine Similarity Scoring             │
     │  for each candidate vs input           │
     └──────────────┬────────────────────────┘
                    │ (scored list)
                    ▼
     ┌───────────────────────────────────────┐
     │  Pick Highest Score                    │
     └──────────────┬────────────────────────┘
                    │
          ┌─────────┴──────────┐
          ▼                    ▼
     Match found          No match
          │                    │
          ▼                    ▼
   Return Entity      Return Fallback Entity
   with SNOMED code   SNOMED_UNKNOWN (261665006)
          │                    │
          └─────────┬──────────┘
                    ▼
     CodeableConcept { code, display, system }
     used in FHIR resource
```

---

## 8. Library / Auto-Configuration Mode

The project can also be used as a reusable library via Spring Boot auto-configuration.

### Configuration Chain

```
FhirMapperConfig (top-level @Configuration)
  ├── imports FhirMapperCoreConfig  → converter beans
  ├── imports FhirMapperDatabaseConfig → @EntityScan + @EnableJpaRepositories
  ├── imports FhirMapperApiConfig   → @ComponentScan for controllers
  └── imports FhirConfiguration     → FHIR context, validator, message converter
```

**`META-INF/spring/AutoConfiguration.imports`**:
```
in.nha.abdm.fhir.mapper.rest.config.library.FhirMapperConfig
```

### Feature Flags

| Property | Default | Effect |
|----------|---------|--------|
| `fhir.mapper.api.enabled` | `true` | Enables/disables REST controllers |
| `fhir.validation.enabled` | `true` | Enables/disables FHIR validation |
| `fhir.validation.fail-on-error` | `true` | Throws exception or logs warning |
| `fhir.validation.log-details` | `true` | Logs validation details |

---

## 9. All REST Endpoints

### Bundle Endpoints

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/v1/bundle/immunization` | Create Immunization FHIR Bundle | `ImmunizationRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/prescription` | Create Prescription FHIR Bundle | `PrescriptionRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/op-consultation` | Create OP Consultation FHIR Bundle | `OPConsultationRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/health-document` | Create Health Document FHIR Bundle | `HealthDocumentRecord` | `Bundle` (201) |
| `POST` | `/v1/bundle/diagnostic-report` | Create Diagnostic Report FHIR Bundle | `DiagnosticReportRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/discharge-summary` | Create Discharge Summary FHIR Bundle | `DischargeSummaryRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/wellness-record` | Create Wellness Record FHIR Bundle | `WellnessRecordRequest` | `Bundle` (201) |
| `POST` | `/v1/bundle/invoice` | Create Invoice FHIR Bundle | `InvoiceBundleRequest` | `Bundle` (201) |

### Validation Endpoint

| Method | Path | Description | Request Body | Response |
|--------|------|-------------|--------------|----------|
| `POST` | `/v1/bundle/validate` | Validate a FHIR Bundle | `Bundle` | `ValidationResult` |

### SNOMED Reference Endpoint

| Method | Path | Description | Path Variable | Response |
|--------|------|-------------|---------------|----------|
| `GET` | `/v1/snomed/{resource}` | Retrieve SNOMED CT codes | See 7.5 above | `SnomedResponse` |

---

*Document generated from codebase analysis. FHIR Mapper v1.0 — ABDM (Ayushman Bharat Digital Mission)*
