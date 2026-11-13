package ch.emed.converter.chmed23a.mapping;

import ch.emed.converter.chmed23a.model.Application;
import ch.emed.converter.chmed23a.model.DosageObject;
import ch.emed.converter.chmed23a.model.Posology;
import ch.emed.converter.chmed23a.model.PosologyDetail;
import ch.emed.converter.chmed23a.model.SequenceObject;
import ch.emed.converter.chmed23a.model.TimeUnit;
import ch.emed.converter.chmed23a.model.TimedDosage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Localized description of any CHMED23A posology (de, fr, it, en), used where CH EMED cannot hold
 * the structure (mapping spec M23-DOS-09) and for FreeText in the reverse direction. Equal input
 * gives equal text; the text holds no patient data.
 */
public final class PosologyText {

  private enum W {
    MORNING,
    NOON,
    EVENING,
    NIGHT,
    ONCE,
    EVERY,
    TIMES,
    THEN,
    PAUSE,
    REPEAT,
    DAYS_OF_MONTH,
    MIN_INTERVAL,
    FROM,
    TO,
    OVER,
    AS_NEEDED,
    BEFORE_MEAL,
    DURING_MEAL,
    AFTER_MEAL,
    EMPTY_STOMACH,
    HIGH_FAT_MEAL,
    LIGHT_MEAL,
    MAIN_MEAL,
    FOR
  }

  private static final Map<String, Map<W, String>> WORDS =
      Map.of(
          "de",
          words(
              "morgens",
              "mittags",
              "abends",
              "nachts",
              "einmalig",
              "alle",
              "x",
              "dann",
              "Pause",
              "wiederholen",
              "am Tag des Monats",
              "mindestens im Abstand von",
              "von",
              "bis",
              "über",
              "bei Bedarf",
              "vor dem Essen",
              "während dem Essen",
              "nach dem Essen",
              "auf nüchternen Magen",
              "mit einer fettreichen Mahlzeit",
              "mit einer leichten Mahlzeit",
              "mit einer Hauptmahlzeit",
              "während"),
          "fr",
          words(
              "le matin",
              "à midi",
              "le soir",
              "la nuit",
              "une seule fois",
              "tous les",
              "x",
              "puis",
              "pause",
              "répéter",
              "le jour du mois",
              "intervalle minimal de",
              "de",
              "à",
              "sur",
              "si nécessaire",
              "avant le repas",
              "pendant le repas",
              "après le repas",
              "à jeun",
              "avec un repas riche en graisses",
              "avec un repas léger",
              "avec un repas principal",
              "pendant"),
          "it",
          words(
              "la mattina",
              "a mezzogiorno",
              "la sera",
              "la notte",
              "una sola volta",
              "ogni",
              "x",
              "poi",
              "pausa",
              "ripetere",
              "il giorno del mese",
              "intervallo minimo di",
              "da",
              "a",
              "in",
              "al bisogno",
              "prima del pasto",
              "durante il pasto",
              "dopo il pasto",
              "a stomaco vuoto",
              "con un pasto ricco di grassi",
              "con un pasto leggero",
              "con un pasto principale",
              "per"),
          "en",
          words(
              "morning",
              "noon",
              "evening",
              "night",
              "once",
              "every",
              "x",
              "then",
              "pause",
              "repeat",
              "on day of month",
              "at least",
              "from",
              "to",
              "over",
              "as needed",
              "before meal",
              "during meal",
              "after meal",
              "on an empty stomach",
              "with a high-fat meal",
              "with a light meal",
              "with a main meal",
              "for"));

  private static final Map<String, String[]> UNITS =
      Map.of(
          "de",
              new String[] {
                "Sekunde(n)", "Minute(n)", "Stunde(n)", "Tag(e)", "Woche(n)", "Monat(e)", "Jahr(e)"
              },
          "fr",
              new String[] {
                "seconde(s)", "minute(s)", "heure(s)", "jour(s)", "semaine(s)", "mois", "année(s)"
              },
          "it",
              new String[] {
                "secondo/i", "minuto/i", "ora/e", "giorno/i", "settimana/e", "mese/i", "anno/i"
              },
          "en",
              new String[] {
                "second(s)", "minute(s)", "hour(s)", "day(s)", "week(s)", "month(s)", "year(s)"
              });

  private static final Map<String, String[]> WEEKDAYS =
      Map.of(
          "de", new String[] {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"},
          "fr", new String[] {"lu", "ma", "me", "je", "ve", "sa", "di"},
          "it", new String[] {"lu", "ma", "me", "gi", "ve", "sa", "do"},
          "en", new String[] {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"});

  private final Map<W, String> w;
  private final String[] units;
  private final String[] weekdays;
  private final String unit;

  private PosologyText(String language, String unit) {
    String key = language == null || language.length() < 2 ? "de" : language.substring(0, 2);
    this.w = WORDS.getOrDefault(key, WORDS.get("de"));
    this.units = UNITS.getOrDefault(key, UNITS.get("de"));
    this.weekdays = WEEKDAYS.getOrDefault(key, WEEKDAYS.get("de"));
    this.unit = unit;
  }

  /**
   * Describes a posology.
   *
   * @param pos the posology
   * @param language CH EMED language such as {@code de-CH}
   * @return the text
   */
  public static String describe(Posology pos, String language) {
    PosologyText t = new PosologyText(language, pos.unit());
    List<String> parts = new ArrayList<>();
    if (pos.po() != null) {
      parts.add(t.detail(pos.po()));
    }
    String meal = t.meal(pos.relMeal());
    if (meal != null) {
      parts.add(meal);
    }
    if (Boolean.TRUE.equals(pos.inRes())) {
      parts.add(t.w.get(W.AS_NEEDED));
    }
    return String.join(", ", parts);
  }

  /** Localized label of a relation to a meal, or null. */
  public static String mealLabel(Integer relMeal, String language) {
    return new PosologyText(language, null).meal(relMeal);
  }

  private String meal(Integer relMeal) {
    if (relMeal == null) {
      return null;
    }
    return switch (relMeal) {
      case 1 -> w.get(W.BEFORE_MEAL);
      case 2 -> w.get(W.DURING_MEAL);
      case 3 -> w.get(W.AFTER_MEAL);
      case 4 -> w.get(W.EMPTY_STOMACH);
      case 5 -> w.get(W.HIGH_FAT_MEAL);
      case 6 -> w.get(W.LIGHT_MEAL);
      case 7 -> w.get(W.MAIN_MEAL);
      default -> null;
    };
  }

  private String detail(PosologyDetail detail) {
    return switch (detail) {
      case PosologyDetail.Daily d -> daily(d.ds());
      case PosologyDetail.FreeText f -> f.text();
      case PosologyDetail.Single s -> w.get(W.ONCE) + ": " + timed(s.tdo());
      case PosologyDetail.Cyclic c -> {
        String every = w.get(W.EVERY) + " " + number(c.cyDu()) + " " + unitName(c.cyDuU());
        String times =
            c.timedDosagesPerCycle() > 1 ? " " + c.timedDosagesPerCycle() + w.get(W.TIMES) : "";
        yield every + times + ": " + timed(c.tdo());
      }
      case PosologyDetail.Sequence s -> {
        List<String> parts = new ArrayList<>();
        for (SequenceObject so : s.sos()) {
          String duration = number(so.du()) + " " + unitName(so.duU());
          parts.add(
              switch (so) {
                case SequenceObject.PosologySequence ps ->
                    w.get(W.FOR)
                        + " "
                        + duration
                        + ": "
                        + (ps.po() == null ? "?" : detail(ps.po()));
                case SequenceObject.Pause p -> w.get(W.PAUSE) + " " + duration;
              });
        }
        yield String.join("; " + w.get(W.THEN) + " ", parts) + " (" + w.get(W.REPEAT) + ")";
      }
    };
  }

  private String timed(TimedDosage tdo) {
    if (tdo == null) {
      return "?";
    }
    return switch (tdo) {
      case TimedDosage.DosageOnly d -> dose(d.dose());
      case TimedDosage.Times t -> {
        List<String> parts = new ArrayList<>();
        for (Application.AtTime a : t.ts()) {
          parts.add(time(a.dt()) + " " + dose(a.dose()));
        }
        yield String.join("; ", parts);
      }
      case TimedDosage.DaySegments s -> {
        List<String> parts = new ArrayList<>();
        for (Application.InSegment a : s.ss()) {
          parts.add(segment(a.s()) + " " + dose(a.dose()));
        }
        yield String.join("; ", parts);
      }
      case TimedDosage.WeekDays d -> {
        List<String> days = new ArrayList<>();
        for (Integer day : d.wds()) {
          days.add(day != null && day >= 1 && day <= 7 ? weekdays[day - 1] : "?");
        }
        yield String.join(", ", days) + ": " + timed(d.tdo());
      }
      case TimedDosage.DaysOfMonth d -> {
        List<String> days = new ArrayList<>();
        for (Integer day : d.doms()) {
          days.add(String.valueOf(day));
        }
        yield w.get(W.DAYS_OF_MONTH) + " " + String.join(", ", days) + ": " + timed(d.tdo());
      }
      case TimedDosage.Interval i ->
          dose(i.dose())
              + ", "
              + w.get(W.MIN_INTERVAL)
              + " "
              + number(i.miDu())
              + " "
              + unitName(i.miDuU());
    };
  }

  private String dose(DosageObject dose) {
    if (dose == null) {
      return "?";
    }
    String u = unit == null ? "" : " " + unit;
    return switch (dose) {
      case DosageObject.Simple s -> amount(s.a()) + u;
      case DosageObject.Range r -> amount(r.aMin()) + "-" + amount(r.aMax()) + u;
      case DosageObject.FromTo f ->
          w.get(W.FROM)
              + " "
              + amount(f.aFrom())
              + " "
              + w.get(W.TO)
              + " "
              + amount(f.aTo())
              + u
              + " "
              + w.get(W.OVER)
              + " "
              + number(f.du())
              + " "
              + unitName(f.duU());
    };
  }

  private String daily(List<BigDecimal> ds) {
    W[] segments = {W.MORNING, W.NOON, W.EVENING, W.NIGHT};
    List<String> parts = new ArrayList<>();
    for (int i = 0; i < ds.size() && i < 4; i++) {
      if (ds.get(i).signum() != 0) {
        parts.add(w.get(segments[i]) + " " + amount(ds.get(i)) + (unit == null ? "" : " " + unit));
      }
    }
    return parts.isEmpty() ? "0" : String.join("; ", parts);
  }

  private String segment(Integer s) {
    if (s == null || s < 1 || s > 4) {
      return "?";
    }
    return w.get(new W[] {W.MORNING, W.NOON, W.EVENING, W.NIGHT}[s - 1]);
  }

  private static String time(String dt) {
    return dt == null ? "?" : dt.length() >= 5 ? dt.substring(0, 5) : dt;
  }

  private String unitName(Integer code) {
    TimeUnit u = TimeUnit.of(code);
    return u == null ? "?" : units[u.code() - 1];
  }

  private static String number(Integer value) {
    return value == null ? "?" : value.toString();
  }

  private static String amount(BigDecimal value) {
    return value == null ? "?" : value.stripTrailingZeros().toPlainString();
  }

  private static Map<W, String> words(String... values) {
    W[] keys = W.values();
    if (values.length != keys.length) {
      throw new IllegalStateException("Expected " + keys.length + " words");
    }
    Map<W, String> map = new java.util.EnumMap<>(W.class);
    for (int i = 0; i < keys.length; i++) {
      map.put(keys[i], values[i]);
    }
    return Map.copyOf(map);
  }
}
