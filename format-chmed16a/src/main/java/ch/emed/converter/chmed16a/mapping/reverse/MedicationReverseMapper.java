package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.Profiles;
import ch.emed.converter.chmed16a.model.Medicament;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Type;

/**
 * CH EMED MedicationStatement → CHMED16A Medicament (mapping spec M-REV-10..M-REV-13, M-REV-24).
 */
public final class MedicationReverseMapper {

  private static final Pattern PHARMACODE = Pattern.compile("^Pharmacode (\\d{1,7})$");
  private static final Pattern PRODUCT_NUMBER = Pattern.compile("^Product number (\\S.*)$");

  private MedicationReverseMapper() {}

  /**
   * Maps one statement.
   *
   * @param statement the statement
   * @param path path of the statement, e.g. {@code MedicationStatement[0]}
   * @param scope mapping scope
   * @return the medicament, or null if the statement is excluded or cannot be mapped (report
   *     entries state why)
   */
  @MappingRule({"M-REV-10", "M-REV-11", "M-REV-12", "M-REV-13", "M-REV-24"})
  public static Medicament map(MedicationStatement statement, String path, ReverseScope scope) {
    if (!included(statement, path, scope)) {
      return null;
    }
    Medication medication = medication(statement.getMedication(), statement.getContained(), scope);
    String medPath = medication == null ? null : medicationPath(statement.getMedication(), path);
    String id = null;
    Integer idType = null;
    if (medication != null) {
      String[] identification = identification(medication, medPath, scope);
      if (identification != null) {
        id = identification[0];
        idType = Integer.valueOf(identification[1]);
        details(medication, medPath, idType == Medicament.ID_TYPE_GTIN, scope);
      }
    }
    if (id == null) {
      scope.missing(
          (medPath == null ? path + ".medication" : medPath) + ".code",
          "The product identification (code or text) is required by CHMED16A");
      return null;
    }
    extensions(statement.getExtension(), path, scope);
    if (statement.hasNote()) {
      scope.unmappedWarning(path + ".note", "The statement note is not carried over", true);
    }

    DosageFromFhirMapper.Result dosage =
        DosageFromFhirMapper.map(statement.getDosage(), path, scope);
    return new Medicament(
        id,
        idType,
        dosage.pos(),
        dosage.unit(),
        reasons(statement, path, scope),
        dosage.appInstr(),
        selfMedication(statement) ? 1 : 0,
        null,
        dosage.roa(),
        null,
        null,
        null,
        null,
        List.of());
  }

  /**
   * A mapped prescription medicament.
   *
   * @param medicament the CHMED16A medicament
   * @param receiver GLN of the intended receiver, or null
   */
  public record Prescribed(Medicament medicament, String receiver) {}

  /**
   * Maps one MedicationRequest of a prescription (M-REV-32..M-REV-40).
   *
   * @param request the request
   * @param path path of the request, e.g. {@code MedicationRequest[0]}
   * @param prescriptionDate date of the prescription (date part of {@code Composition.date})
   * @param scope mapping scope
   * @return the medicament, or null if the request is excluded or cannot be mapped
   */
  @MappingRule({"M-REV-32", "M-REV-33", "M-REV-34"})
  public static Prescribed map(
      MedicationRequest request, String path, String prescriptionDate, ReverseScope scope) {
    if (!included(request, path, scope)) {
      return null;
    }
    Medication medication = medication(request.getMedication(), request.getContained(), scope);
    String medPath = medicationPath(request.getMedication(), path);
    String[] identification =
        medication == null ? null : identification(medication, medPath, scope);
    if (identification == null) {
      scope.missing(
          medPath + ".code", "The product identification (code or text) is required by CHMED16A");
      return null;
    }
    int idType = Integer.parseInt(identification[1]);
    details(medication, medPath, idType == Medicament.ID_TYPE_GTIN, scope);
    extensions(request.getExtension(), path, scope);
    if (request.hasNote()) {
      scope.unmappedWarning(path + ".note", "The request note is not carried over", true);
    }
    DosageFromFhirMapper.Result dosage =
        DosageFromFhirMapper.map(
            request.getDosageInstruction(),
            path,
            scope,
            DosageFromFhirMapper.Mode.prescription(prescriptionDate));
    PrescriptionDetailsReverseMapper.Details details =
        PrescriptionDetailsReverseMapper.map(request, path, scope);
    return new Prescribed(
        new Medicament(
            identification[0],
            idType,
            dosage.pos(),
            dosage.unit(),
            null,
            dosage.appInstr(),
            null,
            null,
            null,
            details.rep(),
            details.subs(),
            details.nbPack(),
            details.indC(),
            List.of()),
        details.receiver());
  }

  /** M-REV-32: request status decides whether the medication is carried over. */
  private static boolean included(MedicationRequest request, String path, ReverseScope scope) {
    String statusPath = path + ".status";
    MedicationRequestStatus status = request.getStatus();
    if (status == null) {
      status = MedicationRequestStatus.UNKNOWN;
    }
    switch (status) {
      case ACTIVE, COMPLETED:
        return true;
      case DRAFT, ONHOLD, UNKNOWN:
        scope.unmappedWarning(
            statusPath,
            "Medication included, but its status "
                + status.toCode()
                + " is not representable in CHMED16A",
            true);
        return true;
      case STOPPED, CANCELLED:
        scope.unmappedWarning(
            statusPath,
            "Medication excluded: its status "
                + status.toCode()
                + " is not representable in CHMED16A",
            true);
        return false;
      default:
        scope.unmapped(statusPath, "Medication excluded: it was entered in error");
        return false;
    }
  }

  // ---------------------------------------------------------------------------------------------

  /** M-REV-10: statement status decides whether the medication is carried over. */
  private static boolean included(MedicationStatement st, String path, ReverseScope scope) {
    String statusPath = path + ".status";
    MedicationStatementStatus status = st.getStatus();
    if (status == null) {
      status = MedicationStatementStatus.UNKNOWN;
    }
    switch (status) {
      case ACTIVE, INTENDED, COMPLETED:
        return true;
      case ONHOLD, UNKNOWN:
        scope.unmappedWarning(
            statusPath,
            "Medication included, but its status "
                + status.toCode()
                + " is not representable in CHMED16A",
            true);
        return true;
      case STOPPED, NOTTAKEN:
        scope.unmappedWarning(
            statusPath,
            "Medication excluded: its status "
                + status.toCode()
                + " is not representable in CHMED16A",
            true);
        return false;
      default:
        scope.unmapped(statusPath, "Medication excluded: it was entered in error");
        return false;
    }
  }

  static Medication medication(Type medication, List<Resource> contained, ReverseScope scope) {
    if (medication instanceof Reference reference) {
      String ref = reference.getReference();
      if (ref != null && ref.startsWith("#")) {
        for (Resource candidate : contained) {
          if (candidate instanceof Medication m && ref.substring(1).equals(candidate.getIdPart())) {
            return m;
          }
        }
        return null;
      }
      return scope.document().resolve(reference, Medication.class);
    }
    if (medication instanceof CodeableConcept concept) {
      Medication synthetic = new Medication();
      synthetic.setCode(concept);
      return synthetic;
    }
    return null;
  }

  static String medicationPath(Type medication, String path) {
    boolean contained =
        medication instanceof Reference reference
            && reference.hasReference()
            && reference.getReference().startsWith("#");
    return path + (contained ? ".contained.Medication" : ".Medication");
  }

  /** M-REV-11; returns {id, idType} or null if no identification is available. */
  private static String[] identification(
      Medication medication, String medPath, ReverseScope scope) {
    CodeableConcept code = medication.getCode();
    String result = null;
    int type = Medicament.ID_TYPE_NONE;
    List<Coding> codings = code.getCoding();
    boolean gtin = false;
    for (Coding coding : codings) {
      if (Profiles.GTIN.equals(coding.getSystem()) && present(coding.getCode())) {
        result = coding.getCode();
        type = Medicament.ID_TYPE_GTIN;
        gtin = true;
        break;
      }
    }
    if (!gtin) {
      String text = code.hasText() ? code.getText() : display(codings);
      if (present(text)) {
        Matcher pharmacode = PHARMACODE.matcher(text.trim());
        Matcher product = PRODUCT_NUMBER.matcher(text.trim());
        if (pharmacode.matches()) {
          result = pharmacode.group(1);
          type = Medicament.ID_TYPE_PHARMACODE;
        } else if (product.matches()) {
          result = product.group(1).trim();
          type = Medicament.ID_TYPE_PRODUCT_NUMBER;
        } else {
          result = text;
        }
      }
    }
    boolean gtinUsed = gtin;
    for (int i = 0; i < codings.size(); i++) {
      Coding coding = codings.get(i);
      if (gtinUsed && Profiles.GTIN.equals(coding.getSystem())) {
        continue;
      }
      scope.unmapped(
          medPath + ".code.coding[" + i + "]",
          "A coding of another code system is not carried over");
    }
    return result == null ? null : new String[] {result, String.valueOf(type)};
  }

  static String display(List<Coding> codings) {
    return codings.stream()
        .map(Coding::getDisplay)
        .filter(d -> present(d))
        .findFirst()
        .orElse(null);
  }

  /** M-REV-24: product details are not carried over; with free-text products that is a loss. */
  private static void details(
      Medication medication, String medPath, boolean gtin, ReverseScope scope) {
    List<String> missing = new ArrayList<>();
    if (medication.hasForm()) {
      missing.add("form");
    }
    if (medication.hasAmount()) {
      missing.add("amount");
    }
    if (medication.hasIngredient()) {
      missing.add("ingredient");
    }
    for (String element : missing) {
      if (gtin) {
        scope.unmapped(
            medPath + "." + element,
            "The product detail is identified by the GTIN; not carried over");
      } else {
        scope.unmappedWarning(
            medPath + "." + element,
            "The product detail cannot be carried over; the product is identified by text only",
            true);
      }
    }
  }

  static void extensions(List<Extension> extensions, String path, ReverseScope scope) {
    for (int i = 0; i < extensions.size(); i++) {
      String url = extensions.get(i).getUrl();
      String where =
          url != null && url.endsWith("ch-emed-ext-treatmentplan")
              ? path + ".extension.treatmentplan"
              : path + ".extension[" + i + "]";
      scope.unmapped(where, "The extension has no CHMED16A counterpart and is not carried over");
    }
  }

  /** M-REV-12. */
  private static String reasons(MedicationStatement st, String path, ReverseScope scope) {
    List<String> reasons = new ArrayList<>();
    for (CodeableConcept reason : st.getReasonCode()) {
      String text = reason.hasText() ? reason.getText() : display(reason.getCoding());
      if (present(text)) {
        reasons.add(text);
      }
    }
    if (reasons.size() > 1) {
      scope.derived(path + ".reasonCode", "Several reasons are joined");
    }
    return reasons.isEmpty() ? null : String.join("; ", reasons);
  }

  /** M-REV-13. */
  private static boolean selfMedication(MedicationStatement st) {
    return st.getCategory().getCoding().stream()
        .anyMatch(
            c ->
                Profiles.STATEMENT_CATEGORY.equals(c.getSystem())
                    && "patientspecified".equals(c.getCode()));
  }
}
