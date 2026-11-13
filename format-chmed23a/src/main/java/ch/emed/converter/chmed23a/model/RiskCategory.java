package ch.emed.converter.chmed23a.model;

import java.util.List;

/** A risk category with its risks (README "RiskCategory"). */
public record RiskCategory(Integer id, List<Integer> rIds) {

  public RiskCategory {
    rIds = rIds == null ? List.of() : List.copyOf(rIds);
  }
}
