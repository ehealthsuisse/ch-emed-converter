package ch.emed.converter.chmed23a.model;

import java.math.BigDecimal;
import java.util.List;

/** PosologyDetail objects (posology.md "PosologyDetail objects"). */
public sealed interface PosologyDetail {

  int type();

  /** t = 1: dosages for morning, noon, evening, night. */
  record Daily(List<BigDecimal> ds) implements PosologyDetail {
    public Daily {
      ds = ds == null ? List.of() : List.copyOf(ds);
    }

    @Override
    public int type() {
      return 1;
    }
  }

  /** t = 2: unstructured posology. */
  record FreeText(String text) implements PosologyDetail {
    @Override
    public int type() {
      return 2;
    }
  }

  /** t = 3: a single application. */
  record Single(TimedDosage tdo) implements PosologyDetail {
    @Override
    public int type() {
      return 3;
    }
  }

  /** t = 4: application at constant intervals; {@code tdpc} absent means 1. */
  record Cyclic(Integer cyDuU, Integer cyDu, TimedDosage tdo, Integer tdpc)
      implements PosologyDetail {
    @Override
    public int type() {
      return 4;
    }

    public int timedDosagesPerCycle() {
      return tdpc == null ? 1 : tdpc;
    }
  }

  /** t = 5: posologies and pauses in sequence. */
  record Sequence(List<SequenceObject> sos) implements PosologyDetail {
    public Sequence {
      sos = sos == null ? List.of() : List.copyOf(sos);
    }

    @Override
    public int type() {
      return 5;
    }
  }
}
