package ch.emed.converter.chmed23a.model;

import java.util.List;

/** TimedDosage objects (posology.md "TimedDosage objects"). */
public sealed interface TimedDosage {

  int type();

  /** t = 1: a dosage without a moment. */
  record DosageOnly(DosageObject dose) implements TimedDosage {
    @Override
    public int type() {
      return 1;
    }
  }

  /** t = 2: dosages at times of day. */
  record Times(List<Application.AtTime> ts) implements TimedDosage {
    public Times {
      ts = ts == null ? List.of() : List.copyOf(ts);
    }

    @Override
    public int type() {
      return 2;
    }
  }

  /** t = 3: dosages in day segments. */
  record DaySegments(List<Application.InSegment> ss) implements TimedDosage {
    public DaySegments {
      ss = ss == null ? List.of() : List.copyOf(ss);
    }

    @Override
    public int type() {
      return 3;
    }
  }

  /** t = 4: on days of the week (1 Monday ... 7 Sunday). */
  record WeekDays(List<Integer> wds, TimedDosage tdo) implements TimedDosage {
    public WeekDays {
      wds = wds == null ? List.of() : List.copyOf(wds);
    }

    @Override
    public int type() {
      return 4;
    }
  }

  /** t = 5: on days of the month (1-28). */
  record DaysOfMonth(List<Integer> doms, TimedDosage tdo) implements TimedDosage {
    public DaysOfMonth {
      doms = doms == null ? List.of() : List.copyOf(doms);
    }

    @Override
    public int type() {
      return 5;
    }
  }

  /** t = 6: with a minimal interval between two applications. */
  record Interval(DosageObject dose, Integer miDu, Integer miDuU) implements TimedDosage {
    @Override
    public int type() {
      return 6;
    }
  }
}
