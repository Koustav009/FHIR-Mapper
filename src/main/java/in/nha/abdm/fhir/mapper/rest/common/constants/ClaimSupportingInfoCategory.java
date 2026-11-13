/* (C) 2026 */
package in.nha.abdm.fhir.mapper.rest.common.constants;

import java.util.Map;

public final class ClaimSupportingInfoCategory {

  private static final Map<String, String> DISPLAY_BY_CODE =
      Map.ofEntries(
          Map.entry("POI", "Proof of identity"),
          Map.entry("POA", "Proof of address"),
          Map.entry("DOB", "Proof of Date of Birth"),
          Map.entry("POR", "Proof of relation"),
          Map.entry("PHT", "Photograph"),
          Map.entry("BVC", "Benefiaciary verification card"),
          Map.entry("DEF", "Declaration form"),
          Map.entry("SIG", "Signature"),
          Map.entry("FCF", "Filled claim form"),
          Map.entry("CER", "Medical Certficate"),
          Map.entry("MB", "Medical bill"),
          Map.entry("DIA", "Diagnostic report"),
          Map.entry("HDS", "Hospital discharge summary"),
          Map.entry("REF", "Referal latter"),
          Map.entry("DEL", "Doctor signed extention letter"),
          Map.entry("CD", "Clinical document"),
          Map.entry("EID", "Employee id card"),
          Map.entry("FIR", "FIR copy"),
          Map.entry("CIL", "Claim status intimation letter"),
          Map.entry("INF", "Additional info related to claim"),
          Map.entry("DIS", "Discharge status and discharge to location detail"),
          Map.entry("ONS", "Period, start or end dates of aspects of the Condition"),
          Map.entry("REL", "Related service"),
          Map.entry("EXC", "Exception"),
          Map.entry("MAT", "Materials Forwarded"),
          Map.entry("ATT", "Attachment"),
          Map.entry("OTH", "Other"),
          Map.entry("COI", "Injury or accident detail"),
          Map.entry("VRE", "Patient Reason for Visit"),
          Map.entry("CRD", "Claim received"),
          Map.entry("NMI", "Claim query detail"),
          Map.entry("TRD", "Treatment detail"),
          Map.entry("IND", "Indicator flag"),
          Map.entry("IMP", "Document Type - Implant"),
          Map.entry("INV", "Document Type - Investigation"),
          Map.entry("DRUG", "Document Type - Drug"),
          Map.entry("PCT", "Document Type - Patient Consent"),
          Map.entry("DCT", "Document Type - Doctor Consent"),
          Map.entry("HCT", "Document Type - Hospital Consent"));

  private ClaimSupportingInfoCategory() {}

  public static String getDisplay(String code) {
    return DISPLAY_BY_CODE.get(code);
  }
}
