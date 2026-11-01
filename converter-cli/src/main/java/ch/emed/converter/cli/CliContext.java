package ch.emed.converter.cli;

import ch.emed.converter.core.api.Converter;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.function.Supplier;

/** Streams and lazily created converter shared by the commands of one CLI run. */
final class CliContext {

  private final Supplier<Converter> supplier;
  private Converter converter;
  final InputStream in;
  final PrintStream out;
  final PrintStream err;

  CliContext(Supplier<Converter> supplier, InputStream in, PrintStream out, PrintStream err) {
    this.supplier = supplier;
    this.in = in;
    this.out = out;
    this.err = err;
  }

  Converter converter() {
    if (converter == null) {
      converter = supplier.get();
    }
    return converter;
  }
}
