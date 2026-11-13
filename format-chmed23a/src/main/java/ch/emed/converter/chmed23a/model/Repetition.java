package ch.emed.converter.chmed23a.model;

/** Repetition object of a prescribed medicament (README "Repetition object"). */
public sealed interface Repetition {

  int type();

  /** t = 1: number of repetitions after the first redemption. */
  record Number(Integer v) implements Repetition {
    @Override
    public int type() {
      return 1;
    }
  }

  /** t = 2: time frame in which the prescription can be redeemed. */
  record Duration(Integer d, Integer u) implements Repetition {
    @Override
    public int type() {
      return 2;
    }
  }

  /** t = 3: both. */
  record NumberAndDuration(Integer v, Integer d, Integer u) implements Repetition {
    @Override
    public int type() {
      return 3;
    }
  }
}
