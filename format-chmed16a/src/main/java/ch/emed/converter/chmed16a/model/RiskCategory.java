package ch.emed.converter.chmed16a.model;

import java.util.List;

/**
 * CHMED16A RiskCategory. An empty risk list means the whole category is explicitly excluded.
 *
 * @param id risk category id (1 renal, 2 liver, 3 reproduction, 4 athlete, 5 vehicles, 6 allergies,
 *     7 diabetes)
 * @param r risk ids within the category
 */
public record RiskCategory(Integer id, List<Integer> r) {

  public RiskCategory {
    r = r == null ? List.of() : List.copyOf(r);
  }
}
