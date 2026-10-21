package ch.emed.converter.chmed16a.mapping.reverse;

import static ch.emed.converter.chmed16a.mapping.PatientMapper.present;

import ch.emed.converter.chmed16a.mapping.MappingRule;
import ch.emed.converter.chmed16a.mapping.PrescriptionDetailsMapper;
import ch.emed.converter.chmed16a.mapping.Profiles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestDispenseRequestComponent;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Type;

/**
 * CH EMED MedicationRequest prescription details → CHMED16A {@code NbPack}, {@code Rep}, {@code
 * Subs}, {@code IndC} and {@code Rcv} (mapping spec M-REV-35..M-REV-39).
 */
public final class PrescriptionDetailsReverseMapper {

  /**
   * Mapped details.
   *
   * @param rep repetition in months, or null
   * @param subs substitution rule (1 = must not be substituted), or null
   * @param nbPack number of packages, or null
   * @param indC indication code, or null
   * @param receiver GLN of the intended receiver, or null
   */
  public record Details(Integer rep, Integer subs, Long nbPack, String indC, String receiver) {}

  private static final Pattern INDICATION =
      Pattern.compile(
          "^" + Pattern.quote(PrescriptionDetailsMapper.INDICATION_PREFIX) + "(\\d{5}\\.\\d{2})$");
  private static final long MAX_NB_PACK = 9_999_999_999L;
  private static final int MAX_REP = 99;

  private PrescriptionDetailsReverseMapper() {}

  /**
   * Maps the prescription-specific elements of a request.
   *
   * @param request the request
   * @param path path of the request, e.g. {@code MedicationRequest[0]}
   * @param scope mapping scope
   * @return the details; elements without counterpart are reported
   */
  @MappingRule({"M-REV-35", "M-REV-36", "M-REV-37", "M-REV-38", "M-REV-39"})
  public static Details map(MedicationRequest request, String path, ReverseScope scope) {
    Integer rep = null;
    Long nbPack = null;
    String receiver = null;
    if (request.hasDispenseRequest()) {
      MedicationRequestDispenseRequestComponent dispense = request.getDispenseRequest();
      String base = path + ".dispenseRequest";
      if (dispense.hasQuantity()) {
        nbPack = packages(dispense.getQuantity(), base + ".quantity", scope);
      }
      if (dispense.hasValidityPeriod()) {
        rep = repetition(dispense, base + ".validityPeriod", scope);
      }
      if (dispense.hasNumberOfRepeatsAllowed()) {
        scope.unmappedWarning(
            base + ".numberOfRepeatsAllowed",
            "The number of repeats has no CHMED16A counterpart and is not carried over",
            true);
      }
      if (dispense.hasPerformer()) {
        receiver = receiver(dispense.getPerformer(), base + ".performer", scope);
      }
    }
    return new Details(
        rep,
        substitution(request, path, scope),
        nbPack,
        indication(request, path, scope),
        receiver);
  }

  /** M-REV-35. */
  private static Long packages(Quantity q, String path, ReverseScope scope) {
    boolean packageUnit =
        Profiles.SNOMED.equals(q.getSystem())
            && PrescriptionDetailsMapper.PACKAGE_CODE.equals(q.getCode());
    BigDecimal value = q.getValue();
    boolean whole = value != null && value.signum() > 0 && value.stripTrailingZeros().scale() <= 0;
    if (packageUnit && whole && value.compareTo(BigDecimal.valueOf(MAX_NB_PACK)) <= 0) {
      return value.longValueExact();
    }
    scope.unmappedWarning(
        path,
        "Only a whole number of packages can be carried as NbPack; the quantity is not carried over",
        true);
    return null;
  }

  /** M-REV-36: validity = start + n months − 1 day. */
  private static Integer repetition(
      MedicationRequestDispenseRequestComponent dispense, String path, ReverseScope scope) {
    var period = dispense.getValidityPeriod();
    LocalDate start = date(period.hasStart() ? period.getStartElement().getValueAsString() : null);
    LocalDate end = date(period.hasEnd() ? period.getEndElement().getValueAsString() : null);
    if (start != null && end != null) {
      for (int months = 1; months <= MAX_REP; months++) {
        if (start.plusMonths(months).minusDays(1).equals(end)) {
          return months;
        }
      }
    }
    scope.unmappedWarning(
        path,
        "The validity period is not a whole number of months from its start; not carried over",
        true);
    return null;
  }

  private static LocalDate date(String value) {
    if (value == null || value.length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(value.substring(0, 10));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** M-REV-37. */
  private static Integer substitution(MedicationRequest request, String path, ReverseScope scope) {
    if (!request.hasSubstitution() || !request.getSubstitution().hasAllowed()) {
      return null;
    }
    Type allowed = request.getSubstitution().getAllowed();
    if (allowed instanceof BooleanType bool) {
      return bool.booleanValue() ? 0 : 1;
    }
    if (allowed instanceof CodeableConcept concept) {
      String code = concept.getCodingFirstRep().getCode();
      if ("E".equals(code)) {
        return 0;
      }
      if (!"N".equals(code)) {
        scope.unmappedWarning(
            path + ".substitution",
            "This substitution code is read as \"do not substitute\"; its specific meaning is not"
                + " carried over",
            true);
      }
      return 1;
    }
    return null;
  }

  /** M-REV-38. */
  private static String indication(MedicationRequest request, String path, ReverseScope scope) {
    String indC = null;
    List<CodeableConcept> reasons = request.getReasonCode();
    for (int i = 0; i < reasons.size(); i++) {
      String text = reasons.get(i).hasText() ? reasons.get(i).getText() : null;
      Matcher m = text == null ? null : INDICATION.matcher(text.trim());
      if (m != null && m.matches() && indC == null) {
        indC = m.group(1);
      } else {
        scope.unmappedWarning(
            path + ".reasonCode[" + i + "]",
            "A prescription has no treatment reason; the reason is not carried over",
            true);
      }
    }
    return indC;
  }

  /** M-REV-39. */
  private static String receiver(Reference performer, String path, ReverseScope scope) {
    String gln = gln(performer.hasIdentifier() ? List.of(performer.getIdentifier()) : List.of());
    if (gln == null) {
      Resource resolved = scope.document().resolve(performer);
      if (resolved instanceof Organization organization) {
        gln = gln(organization.getIdentifier());
      } else if (resolved instanceof Practitioner practitioner) {
        gln = gln(practitioner.getIdentifier());
      }
    }
    if (gln == null) {
      scope.unmapped(path, "The intended receiver has no GLN and is not carried over");
    }
    return gln;
  }

  private static String gln(List<Identifier> identifiers) {
    return identifiers.stream()
        .filter(i -> Profiles.GLN.equals(i.getSystem()) && present(i.getValue()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(null);
  }
}
