package ch.emed.converter.chmed23a.model;

/** Sequence objects (posology.md "Sequence objects"). */
public sealed interface SequenceObject {

  int type();

  Integer du();

  Integer duU();

  /** t = 1: a posology for a duration. */
  record PosologySequence(Integer du, Integer duU, PosologyDetail po) implements SequenceObject {
    @Override
    public int type() {
      return 1;
    }
  }

  /** t = 2: a pause for a duration. */
  record Pause(Integer du, Integer duU) implements SequenceObject {
    @Override
    public int type() {
      return 2;
    }
  }
}
