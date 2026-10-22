# AGENTS.md — FHIR Mapper (Project Context for AI Agents)

This file is the **canonical project context**. Read this first; use it to orient yourself before
touching any code. It distils the architecture, conventions, and pitfalls so you rarely need to
read the full source tree. Where deeper detail matters, this file points to the exact source file
or `docs/` document to read.

> If this file and the code disagree, the **code is the source of truth** — update this file.

---

## 1. What This Project Is

**ABDM FHIR Mapper** is a Spring Boot 3 application + embeddable library that maps Indian healthcare
records (ABDM HI-types and NHCX insurance messages) into **FHIR R4 bundles** conformant with the
NRCES implementation guide. Clients (HIS/HMIS) POST a plain-JSON clinical record and receive a
full FHIR R4 Bundle (`application/fhir+json`). "Reverse" endpoints also extract a FHIR bundle back
into the original request DTO, and render it as human-readable HTML.

- **Domain**: Ayushman Bharat Digital Mission (ABDM); base profiles are NRCES ABDM + NHCX.
- **Package root**: `in.nha.abdm.fhir.mapper`
- **Port**: 8085 · **Java 17** (`gradle toolchain`, `.java-version` = 17) · **Gradle ≥ 8.5**
- **FHIR engine**: HAPI FHIR 7.4.0 (R4) · **Spring Boot 3.2.4** · Lombok · Spring Data JPA · H2

Two delivery surfaces, same codebase:

1. **Standalone REST service** — `./gradlew bootRun`, Docker, or `docker-compose-fhir.yaml`.
2. **Reusable Spring Boot library** — auto-configuration exports converters/services for embedding;
   see `README_LIBRARY.md`.

---

## 2. Repo Layout (Top Level)

| Path | Purpose |
|------|---------|
| `src/main/java/in/nha/abdm/fhir/mapper/FHIRApplication.java` | `@SpringBootApplication`, `@EnableCaching` entry point |
| `src/main/java/in/nha/abdm/fhir/mapper/Utils.java` | Shared static FHIR/date helpers (`getFormattedDateTime`, `buildReference`, narrative via `setNarrative`), used by all `Make*` builders |
| `src/main/java/in/nha/abdm/fhir/mapper/rest/controller/` | REST controllers: `BundleController`, `ValidationController`, `SnomedController`, `TerminologyController`, `ServiceIndexController` |
| `.../rest/converter/` | **Converters** — one per bundle type; orchestrate the build pipeline (16 total) |
| `.../rest/services/` | Orchestration: `FhirValidationService`, `BundleExtractionService`, `InpsAggregationService`, `BundleDoctorService`, `BundleHtmlRenderService`, per-type `*BundleExtractor`s, `ServiceIndexRenderService` |
| `.../rest/dto/resources/` | **`Make*Resource`** components — each builds one FHIR resource type; `MakeChargeItem*`/`MakeInvoice*` under `invoice/` |
| `.../rest/dto/compositions/` | **`Make*Composition`** — builds the document-index `Composition` per record type; `InpsResources` |
| `.../rest/requests/` | Request DTOs (`*Request`, `*BundleRequest`), nested in `requests/helpers/` |
| `.../rest/database/h2/` | Terminology store: `tables/` (JPA entities), `repositories/`, `services/` (`SnomedService`, `TerminologyService`), `config/DbLoader` |
| `.../rest/common/constants/` | **Every literal** (URLs, profiles, codes, paths, error codes) — nearly zero magic strings in code |
| `.../rest/common/helpers/` | `BundleUtils`, `CompositionUtils`, `ObservationUtils`, response wrappers |
| `.../rest/exceptions/` | `GlobalExceptionHandler` (`@ControllerAdvice`), `FhirMapperException`, `FhirValidationException`, `NotBlankFields` + validator |
| `src/main/resources/snomed/*.json` | Seed terminology data, loaded into H2 at startup by `DbLoader` |
| `src/main/resources/package.tgz` | **NRCES ABDM IG** NPM package (validation profiles) |
| `src/main/resources/ips-package.tgz` | **IPS/INPS** NPM package (used by INPS validation) |
| `src/main/resources/application.properties` | Port, H2 datasource, FHIR-validation flags, logging |
| `src/main/resources/META-INF/spring/...AutoConfiguration.imports` | Registers `FhirMapperConfig` for library auto-config |
| `docs/` | Rich analysis + summary docs (see §9); `fhir-mapper.yaml` = OpenAPI spec |
| `postmanCollection/FHIR.postman_collection.json` | Ready-made requests for every endpoint |

---

## 3. Architecture & Request Flow

Standard layered flow, no exotic wiring:

```
HTTP POST /v1/bundle/{type} (JSON DTO)
  → BundleController (@Valid bean validation + @NotBlankFields)
  → Converter  (3-phase pipeline, below)
  → FhirValidationService.validateBundle()          [only if fhir.validation.enabled]
  → FhirHttpMessageConverter (HAPI JSON parser)     → HTTP 201, application/fhir+json
```

**The 3-phase converter pipeline** (every converter follows this; verified pattern):

1. **Decompose** — call each `Make*Resource` component (patient, practitioner, organisation,
   encounter, condition, observation, medication… ) passing slices of the request + references to
   already-built resources. Each `@Component` returns a complete HAPI FHIR resource with UUID id,
   `meta.profile`, references, and human-readable narrative (`Utils.setNarrative`).
2. **Compose** — `Make*Composition` builds the `Composition` (the document's table of contents).
3. **Assemble** — `BundleUtils.addEntry/addEntries` assign stable `urn:uuid:` ids; bundle gets
   `id`, `timestamp`, `identifier` = careContextReference, `meta.profile`, and type.

**Two record families** (determines bundle type — see `BundleExtractionService` for the clean split):

| Family | Bundle type | Converters |
|--------|-------------|------------|
| **ABDM clinical records** (HI-types) | `DOCUMENT` | immunization, prescription, op-consultation, health-document, diagnostic-report, discharge-summary, wellness-record, invoice, **INPS** |
| **NHCX insurance messages** | `COLLECTION` | coverage-eligibility-request, coverage-eligibility-response, claim, claim-response, insurance-plan, payment-notice, task-bundle-insurance-plan-request |

---

## 4. Terminology Engine (the fuzzy-matching core)

Converters receive **free text** ("Fever", "Aspirin 75 mg oral tablet") and must emit coded
`CodeableConcept`s. This is the most distinctive part of the codebase — understand it before
touching any `Make*` builder.

**Startup (`DbLoader`, `@PostConstruct`)**: scans `classpath:/snomed/*.json`; each file seeds one
H2 table via its matching `*Repo` (`findAll`-style bulk insert); creates a `(code, display)`
composite index. Entities are `code`/`display`/`type` and implement `Displayable`.

**Runtime (`SnomedService`, `@Cacheable` per category)** — each `get*Code(display)` method:
1. **Phase A** — `findTop20ByDisplayContainingIgnoreCase(display)` (JPA derived query → ≤20 H2 rows).
2. **Phase B** — fuzzy match: word-count filter (candidate words ≤ input words + 2), then
   **cosine similarity** ranking (`CosineSimilarity` from commons-text); pick the max.
3. **Phase C** — fallback to `SNOMED_UNKNOWN` (`261665006`) when nothing matches; encounter falls
   back to `AMB`.

Result becomes `CodeableConcept` with `text` = original input, `coding.code/display/system`
(`http://snomed.info/sct`).

**Categories** (H2 tables): Condition/Procedure, Diagnostic, Encounter, Medicine, MedicineRoute,
Observation, Specimen, Vaccine + TypeInvoice, TypeChargeItem, and newer **LOINC, ICD-11, NAMASTE**.

**Two lookup services**:
- `SnomedService` — typed `get*Code()` per category, used by `Make*` builders (see §5).
- `TerminologyService` — generic cross-system search/translate over **all** code sets (SNOMED +
  LOINC + ICD-11 + NAMASTE); backs `/v1/terminology/*`. Register new code systems in its
  `sources()` registry.

---

## 5. Resource Builders → Terminology Calls (reference map)

From `docs/PROJECT_FLOW.md §4`, still accurate:

| Builder | SNOMED calls |
|---------|--------------|
| `MakeConditionResource` | condition/procedure code |
| `MakeObservationResource` / `MakeWellnessObservationResource` | observation code |
| `MakeEncounterResource` | encounter code |
| `MakeMedicationRequestResource` / `MakeInvoiceMedicationResource` | medicine + route codes |
| `MakeProcedureResource`, `MakeAllergyToleranceResource`, `MakeFamilyMemberResource` | condition/procedure code |
| `MakeServiceRequestResource`, `MakeDiagnosticLabResource` | diagnostic + observation codes |
| `MakeImmunizationResource` | vaccine code |
| `MakeCarePlanResource` | observation code |
| NHCX builders (`MakeCoverage*`, `MakeClaim*`, `MakeInsurancePlan*`, `MakePaymentNotice*`, invoice `MakeChargeItemResource` etc.) | medicine / route / existing code mapping |

Newer builders beyond that doc: `MakeConsentResource`, `MakeDeviceResource`,
`MakeDeviceUseStatementResource`, `MakeFlagResource`, `MakeClinicalImpressionResource`,
`MakeImmunizationRecommendationResource`, `MakeCoverageResource`, `MakeMedicationStatementResource`.

---

## 6. FHIR Validation Pipeline

`FhirConfiguration` builds a HAPI `FhirValidator` with a validation-support chain:
NPM profile support (ABDM `package.tgz` + IPS `ips-package.tgz`) → base R4 → in-memory terminology
→ common code systems → **remote `tx.fhir.org`** (offline builds fall back to in-memory) →
snapshot generation, wrapped in a caching support.

Notable behaviors:
- `loadABDMProfiles` logs an **IG-version drift warning** if `package.tgz` version ≠
  `IgVersionConstants.EXPECTED_NRCES_IG_VERSION` (currently NRCES IG 7.0.0).
- `normaliseTargetProfiles` strips `|version` suffixes from `targetProfile` canonicals so
  `Reference.type` validates against unpinned subclass profiles.
- `FhirInstanceValidator` settings: no unknown extensions, `ErrorForUnknownProfiles=true`.

**Flags** (`application.properties`): `fhir.validation.enabled`, `fhir.validation.fail-on-error`,
`fhir.validation.log-details`. In `BundleController.validateAndReturnBundle`: disabled → pass
through; invalid + fail-on-error → `FhirValidationException` (400); invalid + warn → log and
continue. **INPS endpoints skip validation** (see §7). `ValidationController` exposes `/validate`
(strict) and `/lint`.

---

## 7. INPS (Indian Patient Summary) — special handling

- `POST /v1/bundle/inps` — builds an IPS-derived document bundle (NRCES IG 7.0.0, HL7 IPS 2.0.0)
  with 3 mandatory sections (Problems, Allergies, Medications) + ~13 optional sections (Past
  Problems, Pregnancy, Social History, Vital Signs, Plan of Care, Advance Directives, Alerts,
  Functional Status, Patient Story, Immunizations, Procedures, Medical Devices, Results).
- `POST /v1/bundle/inps/aggregate` — builds an INPS from **already-built** clinical-record bundles
  (JSON array body), via `InpsAggregationService`.
- **INPS converters do NOT run FHIR validation** (IPS profiles differ; see commit
  `af7d7ae "Skip FHIR validation on INPS endpoints"`). Builders used: `MakeInpsComposition`,
  `InpsResources`, `MakeImmunizationRecommendationResource`, `MakeMedicationStatementResource`.

---

## 8. Reverse Mapping (extraction & rendering)

- `POST /v1/bundle/extract` — `BundleExtractionService` detects the record type and reproduces the
  original request DTO. NHCX (COLLECTION) bundles are identified by bundle `meta.profile` or key
  resource class; ABDM (DOCUMENT) bundles by the `Composition` type code/title (see per-type
  `*BundleExtractor` in `rest/services/`). Emits `ExtractedBundleResponse` with unresolved-reference
  warnings.
- `POST /v1/bundle/html` — renders that extraction as an HTML page (`BundleHtmlRenderService`).
- `GET /` — small HTML service index (`ServiceIndexController` + `ServiceIndexRenderService`).
  The app does **not** host Swagger UI; the spec lives at venuajitesh.github.io/FHIR-Mapper and
  `docs/fhir-mapper.yaml`.

---

## 9. Complete Endpoint Map

Base `/v1/bundle` (all `POST` unless noted):

| Path | Purpose |
|------|---------|
| `/immunization` `/prescription` `/op-consultation` `/health-document` `/diagnostic-report` `/discharge-summary` `/wellness-record` `/invoice` | ABDM clinical-record bundles (DOCUMENT) |
| `/coverage-eligibility-request` `/coverage-eligibility-response` `/claim` `/claim-response` `/insurance-plan` `/payment-notice` | NHCX insurance bundles (COLLECTION) |
| `/task-bundle-insurance-plan-request` | NHCX `TaskBundle` — poll Task + insurer/hospital Organizations for an InsurancePlan request (COLLECTION) |
| `/inps`, `/inps/aggregate` | INPS bundle; aggregate from existing bundles |
| `/validate` | Validate an arbitrary bundle → `ValidationResult` (strict) |
| `/lint` | `BundleDoctorService` — auto-repair a bundle, report fixes + before/after validation |
| `/extract` | Reverse-map bundle → original request DTO |
| `/html` | Render bundle as HTML |
| `/v1/snomed/{resource}` (GET) | List codes for a category (`Condition`, `Procedure`, `Encounter`, `Medication-Route`, `Medications`, `Observations`, `Specimen`, `Vaccines`) |
| `/v1/terminology/search?q=&category=&limit=` (GET) | Cross-system fuzzy search (SNOMED/LOINC/ICD11/NAMASTE) |
| `/v1/terminology/translate?...` (GET) | Translate a term/code into a target category |
| `/` (GET) | HTML service index |

Success = `201 Created` (bundle endpoints) / `200` (extract, html, validate, lint, GETs).

**Error model** (`GlobalExceptionHandler`): `400` for validation errors
(`ValidationErrorResponse` with per-field messages), parse errors (`ErrorResponse`), business
exceptions (`FacadeError`, code `1000`), and FHIR validation failures (`ValidationResult`).

---

## 10. Code Conventions (follow these)

- **No magic strings**: every URL, profile, code, path, and message lives in a constants class
  under `rest/common/constants/` (`BundleUrlIdentifier`, `ResourceProfileIdentifier`,
  `ControllerMappingConstants`, `SwaggerConstants`, `SnomedCodeIdentifier`, `ValidationConstants`,
  `IgVersionConstants`, …).
- **Builder pattern**: one `MakeXxxResource` `@Component` per FHIR resource type. Constructor
  injection (`@RequiredArgsConstructor`) with `final` fields is the norm; `BundleController` is the
  exception (explicit constructor).
- **DTOs**: Lombok (`@Data`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor`), Jakarta
  Bean Validation annotations (`@NotBlank`, `@Pattern`) plus custom `@NotBlankFields` for nested
  non-empty lists. Request DTO enums validate against `ValidationConstants`.
- **Identifiers**: resources get `UUID` ids; bundle entries referenced via `urn:uuid:`; composition
  is convention entry[0].
- **Dates**: parsed via `Utils.getFormattedDateTime` (strict ISO then lenient fallback); dates
  accept `yyyy-MM-dd` or ISO-8601 with timezone.
- **Formatting**: Google Java Format (Spotless, `spotlessApply` runs after `JavaCompile`), tabs =
  2 indent, 4 continued. Do **not** hand-format; run `./gradlew spotlessApply`.
- **Version drift guard**: bump `IgVersionConstants.EXPECTED_NRCES_IG_VERSION` together with any
  `package.tgz` upgrade; test `IgVersionDriftTest` and `TargetProfileNormalisationTest` pin this.

---

## 11. Build, Test, Run

```bash
./gradlew bootRun          # run server on :8085 (devtools auto-restarts on change; don't use a jar)
./gradlew test             # JUnit 5 platform
./gradlew compileJava      # compile (works offline; deps cached)
./gradlew spotlessApply    # format after edits
docker compose -f docker-compose-fhir.yaml up --build   # containerized alternative
```

- H2 console enabled at `/h2-console` (jdbc `jdbc:h2:mem:fhir_mapper`, user `abdm`).
- Tests live under `src/test/java/...` mirroring `main`. Notable suites: `Make*ResourceTest`
  (patient, invoice, clinical-impression, consent, device…), `FhirExtractionSupportTest`,
  `InpsAggregationServiceTest`, `BundleHtmlRenderServiceTest`, `MakeInpsCompositionTest`,
  `IgVersionDriftTest`.

---

## 12. Docs Index — go deeper here

- **`docs/PROJECT_FLOW.md`** — the best deep-dive: end-to-end flow, converter/resource matrices,
  full SNOMED algorithm, exception table, library config chain. **Start here for "how it works".**
- `docs/fhir-mapper.yaml` — the authoritative OpenAPI schema (read this instead of guessing DTOs).
- `docs/DEPLOYMENT_GUIDE.md`, `docs/FHIR_VALIDATION_README.md`, `docs/BEST_PRACTICES.md` —
  ops/validation guidance.
- `docs/*SUMMARY*.md`, `docs/*REPORT*.txt` — historical change-notes/reports; skim only.
- `README_LIBRARY.md` — embedding as a library, `@ConditionalOnMissingBean` overrides.

---

## 13. Gotchas & Traps

- **Don't run validation against INPS bundles.** INPS export + aggregate intentionally skip the
  validator; force-feeding them through it fails on IPS-specific profiles.
- **Profile-unpinned references matter.** `FhirConfiguration.normaliseTargetProfiles` strips
  versioned canonicals; if you reintroduce `|version` suffixes, `Reference.type` validation breaks
  again (regression #14, commit `72728ef`).
- **Terminology quality = fuzzy match quality.** Free-text misses degrade output silently to
  `261665006 (SNOMED_UNKNOWN)`; add seed terms to `snomed/*.json` when a clinical term is common.
- **NHCX ≠ ABDM.** Six NHCX converters emit `COLLECTION` bundles with their own bundle profiles;
  the extractor dispatches on profile/key-resource first, `Composition` second. Don't mix.
- **Bundles must resolve.** `extract`/`html` warn (not fail) on unresolved references; `/lint`
  repairs them. Keep `urn:uuid` ids stable across build and extract or round-trips break.
- **`fhir.mapper.api.enabled=false`** disables REST controllers in library mode only — core
  converters/services still exist and are `@ConditionalOnMissingBean`-overridable.
- First Gradle build downloads dependencies; prefer `--offline` only after a warm cache.