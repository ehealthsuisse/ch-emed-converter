package ch.emed.converter.chmed16a.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * CHMED16A Posology. {@code d} holds the doses [morning, noon, evening, night]; it is ignored if
 * {@code tt} is specified. {@code cyDu} defaults to 86400 s and is ignored without {@code tt}.
 */
public record Posology(
    String dtFrom, String dtTo, Long cyDu, Integer inRes, List<BigDecimal> d, List<TakingTime> tt) {

  public static final long DEFAULT_CYCLE_SECONDS = 86_400L;

  public Posology {
    d = d == null ? List.of() : List.copyOf(d);
    tt = tt == null ? List.of() : List.copyOf(tt);
  }

  public boolean isReserve() {
    return inRes != null && inRes == 1;
  }

  public long effectiveCycleSeconds() {
    return cyDu == null ? DEFAULT_CYCLE_SECONDS : cyDu;
  }
}
