package ch.emed.converter.rest.api;

import ch.emed.converter.core.api.AuthorDetails;
import ch.emed.converter.core.api.Confidentiality;
import ch.emed.converter.core.api.ConversionOptions;
import ch.emed.converter.core.api.OrganizationDetails;
import ch.emed.converter.core.api.PatientIdentifier;
import ch.emed.converter.rest.error.MalformedRequestException;
import ch.emed.converter.rest.model.RequestOptions;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Maps request options to library options with the same rules as the CLI (research R12): start from
 * {@link ConversionOptions#defaults()} and override only what the request sets. {@code
 * validateOutput}, id strategy, clock and decoded-size limit stay at the library defaults, which
 * keeps the output byte-identical to the CLI (FR-003).
 */
@Component
public class RequestOptionsMapper {

  /**
   * @throws MalformedRequestException naming the property path of an invalid value, never the value
   */
  public ConversionOptions map(RequestOptions request) {
    ConversionOptions options = ConversionOptions.defaults();
    if (request == null) {
      return options;
    }
    if (request.strict() != null) {
      options = options.withStrict(request.strict());
    }
    if (request.language() != null) {
      if (!ConversionOptions.LANGUAGES.contains(request.language())) {
        throw invalid("options.language");
      }
      options = options.withDefaultLanguage(request.language());
    }
    if (request.confidentiality() != null) {
      options = options.withConfidentiality(confidentiality(request.confidentiality()));
    }
    if (request.patientIdentifier() != null) {
      options = options.withPatientIdentifier(patientIdentifier(request.patientIdentifier()));
    }
    if (request.author() != null) {
      options = options.withAuthor(author(request.author()));
    }
    return options;
  }

  private static Confidentiality confidentiality(String value) {
    // lower-case only, as documented; the CLI is lenient but the contract is an enum
    if (!value.equals(value.toLowerCase(Locale.ROOT))) {
      throw invalid("options.confidentiality");
    }
    try {
      return Confidentiality.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw invalid("options.confidentiality");
    }
  }

  private static PatientIdentifier patientIdentifier(RequestOptions.PatientIdentifier id) {
    if (isBlank(id.system())) {
      throw invalid("options.patientIdentifier.system");
    }
    if (isBlank(id.value())) {
      throw invalid("options.patientIdentifier.value");
    }
    return new PatientIdentifier(id.system(), id.value());
  }

  private static AuthorDetails author(RequestOptions.Author author) {
    RequestOptions.Organization org = author.organization();
    return new AuthorDetails(
        author.gln(),
        author.given(),
        author.family(),
        org == null
            ? null
            : new OrganizationDetails(
                org.name(), org.street(), org.zip(), org.city(), org.country()));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static MalformedRequestException invalid(String path) {
    return new MalformedRequestException("Invalid value: " + path);
  }
}
