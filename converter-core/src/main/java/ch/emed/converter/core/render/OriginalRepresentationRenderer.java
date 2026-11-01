package ch.emed.converter.core.render;

import ch.emed.converter.core.report.Phase;
import ch.emed.converter.core.report.ReasonCode;
import ch.emed.converter.core.report.ReportCollector;
import ch.emed.converter.core.report.ReportEntry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Timing;

/**
 * Renders the CH EMED "original representation" PDF from a finished document Bundle (FR-021,
 * research R12). Format-agnostic. Deterministic: the trailer /ID is derived from the Bundle
 * identifier, the creation date is the document date and no producer/time metadata is written.
 */
public final class OriginalRepresentationRenderer {

  private static final float MARGIN = 50;
  private static final float LEADING = 14;
  private static final float FONT_SIZE = 10;
  private static final float TITLE_SIZE = 16;
  private static final int WRAP = 95;

  private static final Map<String, String[]> LABELS =
      Map.of(
          "de", new String[] {"Patient", "Geburtsdatum", "Autor", "Datum", "Grund", "Hinweis"},
          "fr",
              new String[] {"Patient", "Date de naissance", "Auteur", "Date", "Raison", "Remarque"},
          "it", new String[] {"Paziente", "Data di nascita", "Autore", "Data", "Motivo", "Nota"},
          "en", new String[] {"Patient", "Date of birth", "Author", "Date", "Reason", "Note"});

  private OriginalRepresentationRenderer() {}

  /**
   * Renders the PDF.
   *
   * @param bundle finished CH EMED document (without the Binary of the original representation)
   * @param report receives a {@code PDF_CHARACTER_REPLACED} entry if characters were replaced
   * @return PDF bytes
   */
  public static byte[] render(Bundle bundle, ReportCollector report) {
    Composition composition = (Composition) bundle.getEntryFirstRep().getResource();
    String language = composition.hasLanguage() ? composition.getLanguage().substring(0, 2) : "de";
    String[] labels = LABELS.getOrDefault(language.toLowerCase(Locale.ROOT), LABELS.get("de"));

    List<String[]> lines = new ArrayList<>();
    lines.add(new String[] {"T", composition.getTitle()});
    Patient patient = resolve(bundle, composition.getSubject(), Patient.class);
    if (patient != null) {
      lines.add(new String[] {"N", labels[0] + ": " + name(patient.getNameFirstRep())});
      if (patient.hasBirthDate()) {
        lines.add(
            new String[] {
              "N", labels[1] + ": " + patient.getBirthDateElement().getValueAsString()
            });
      }
    }
    if (composition.hasAuthor()) {
      lines.add(
          new String[] {
            "N", labels[2] + ": " + authorName(bundle, composition.getAuthorFirstRep())
          });
    }
    lines.add(
        new String[] {"N", labels[3] + ": " + composition.getDateElement().getValueAsString()});
    lines.add(new String[] {"N", ""});
    for (Composition.SectionComponent section : composition.getSection()) {
      for (Reference ref : section.getEntry()) {
        Resource resource = resolve(bundle, ref, Resource.class);
        if (resource instanceof MedicationStatement statement) {
          addMedication(
              lines,
              statement.getContained(),
              statement.getDosage(),
              labels,
              statement.hasReasonCode() ? statement.getReasonCodeFirstRep().getText() : null);
        } else if (resource instanceof MedicationRequest request) {
          addMedication(
              lines,
              request.getContained(),
              request.getDosageInstruction(),
              labels,
              request.hasReasonCode() ? request.getReasonCodeFirstRep().getText() : null);
        }
      }
      if (section.getCode().getCodingFirstRep().getCode().equals("48767-8") && section.hasText()) {
        lines.add(
            new String[] {"N", labels[5] + ": " + stripTags(section.getText().getDivAsString())});
      }
    }
    return write(bundle, composition, lines, report);
  }

  private static void addMedication(
      List<String[]> lines,
      List<Resource> contained,
      List<Dosage> dosages,
      String[] labels,
      String reason) {
    String name =
        contained.stream()
            .filter(Medication.class::isInstance)
            .map(r -> ((Medication) r).getCode().getText())
            .findFirst()
            .orElse("?");
    lines.add(new String[] {"B", name});
    for (Dosage dosage : dosages) {
      String text = dosageText(dosage);
      if (!text.isEmpty()) {
        lines.add(new String[] {"N", "  " + text});
      }
      if (dosage.hasPatientInstruction()) {
        lines.add(new String[] {"N", "  " + dosage.getPatientInstruction()});
      }
    }
    if (reason != null) {
      lines.add(new String[] {"N", "  " + labels[4] + ": " + reason});
    }
    lines.add(new String[] {"N", ""});
  }

  private static String dosageText(Dosage dosage) {
    StringBuilder sb = new StringBuilder();
    if (dosage.hasText()) {
      sb.append(dosage.getText());
    }
    Timing.TimingRepeatComponent repeat = dosage.getTiming().getRepeat();
    if (repeat.hasBoundsPeriod()) {
      sb.append(repeat.getBoundsPeriod().getStartElement().getValueAsString());
      if (repeat.getBoundsPeriod().hasEnd()) {
        sb.append(" - ").append(repeat.getBoundsPeriod().getEndElement().getValueAsString());
      }
      sb.append(' ');
    }
    repeat.getWhen().forEach(w -> sb.append(w.getCode()).append(' '));
    repeat.getTimeOfDay().forEach(t -> sb.append(t.getValue(), 0, 5).append(' '));
    if (dosage.hasDoseAndRate() && dosage.getDoseAndRateFirstRep().hasDoseQuantity()) {
      var q = dosage.getDoseAndRateFirstRep().getDoseQuantity();
      sb.append(q.getValue().stripTrailingZeros().toPlainString());
      if (q.hasUnit()) {
        sb.append(' ').append(q.getUnit());
      }
    }
    if (dosage.hasAsNeededBooleanType() && dosage.getAsNeededBooleanType().booleanValue()) {
      sb.append(" (PRN)");
    }
    return sb.toString().trim();
  }

  private static String authorName(Bundle bundle, Reference author) {
    Resource resource = resolve(bundle, author, Resource.class);
    if (resource instanceof PractitionerRole role) {
      Practitioner practitioner = resolve(bundle, role.getPractitioner(), Practitioner.class);
      return practitioner == null ? "" : name(practitioner.getNameFirstRep());
    }
    if (resource instanceof Patient patient) {
      return name(patient.getNameFirstRep());
    }
    if (resource instanceof Device device && device.hasDeviceName()) {
      return device.getDeviceNameFirstRep().getName();
    }
    return "";
  }

  private static String name(HumanName name) {
    String given = name.getGivenAsSingleString();
    return (name.getFamily() == null ? "" : name.getFamily())
        + (given == null || given.isEmpty() ? "" : ", " + given);
  }

  private static <T extends Resource> T resolve(Bundle bundle, Reference ref, Class<T> type) {
    if (ref == null || !ref.hasReference()) {
      return null;
    }
    String target = ref.getReference();
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      boolean matches =
          target.equals(entry.getFullUrl())
              || (entry.hasFullUrl() && entry.getFullUrl().endsWith("/" + target))
              || (resource != null
                  && target.equals(resource.fhirType() + "/" + resource.getIdPart()));
      if (matches && type.isInstance(resource)) {
        return type.cast(resource);
      }
    }
    return null;
  }

  private static String stripTags(String html) {
    return html.replaceAll("<[^>]+>", "").trim();
  }

  private static byte[] write(
      Bundle bundle, Composition composition, List<String[]> lines, ReportCollector report) {
    PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    int[] replaced = {0};
    try (PDDocument doc = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDDocumentInformation info = new PDDocumentInformation();
      Calendar created = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
      created.setTime(composition.getDate());
      info.setCreationDate(created);
      info.setTitle(encodable(regular, composition.getTitle(), replaced));
      doc.setDocumentInformation(info);

      PDPage page = null;
      PDPageContentStream content = null;
      float y = 0;
      for (String[] line : lines) {
        PDType1Font font = line[0].equals("N") ? regular : bold;
        float size = line[0].equals("T") ? TITLE_SIZE : FONT_SIZE;
        for (String part : wrap(line[1])) {
          if (content == null || y < MARGIN + LEADING) {
            if (content != null) {
              content.close();
            }
            page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            content = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - MARGIN;
          }
          content.beginText();
          content.setFont(font, size);
          content.newLineAtOffset(MARGIN, y);
          content.showText(encodable(font, part, replaced));
          content.endText();
          y -= line[0].equals("T") ? LEADING * 2 : LEADING;
        }
      }
      if (content != null) {
        content.close();
      }
      byte[] id = documentId(bundle);
      COSArray idArray = new COSArray();
      idArray.add(new COSString(id));
      idArray.add(new COSString(id));
      doc.getDocument().getTrailer().setItem(COSName.ID, idArray);
      doc.save(out);
      if (replaced[0] > 0) {
        report.add(
            ReportEntry.information(
                    ReasonCode.PDF_CHARACTER_REPLACED,
                    Phase.MAP,
                    "Bundle",
                    replaced[0]
                        + " character(s) not supported by the PDF font were replaced in the"
                        + " original representation only")
                .withTargetPath("Binary.data"));
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot render original representation", e);
    }
  }

  private static List<String> wrap(String text) {
    List<String> parts = new ArrayList<>();
    String rest = text == null ? "" : text.replace('\r', ' ').replace('\n', ' ');
    while (rest.length() > WRAP) {
      int cut = rest.lastIndexOf(' ', WRAP);
      if (cut <= 0) {
        cut = WRAP;
      }
      parts.add(rest.substring(0, cut));
      rest = rest.substring(cut).stripLeading();
    }
    parts.add(rest);
    return parts;
  }

  private static String encodable(PDType1Font font, String text, int[] replaced) {
    if (text == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            cp -> {
              String ch = new String(Character.toChars(cp));
              try {
                font.encode(ch);
                sb.append(ch);
              } catch (IllegalArgumentException | IOException e) {
                sb.append('?');
                replaced[0]++;
              }
            });
    return sb.toString();
  }

  private static byte[] documentId(Bundle bundle) {
    try {
      return MessageDigest.getInstance("MD5")
          .digest(bundle.getIdentifier().getValue().getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
