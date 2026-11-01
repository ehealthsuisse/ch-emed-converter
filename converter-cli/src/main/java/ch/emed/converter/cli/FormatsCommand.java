package ch.emed.converter.cli;

import ch.emed.converter.core.api.ConversionPair;
import ch.emed.converter.core.api.Converter;
import java.util.Comparator;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/** Lists supported conversions and pinned specification versions. */
@Command(name = "formats", description = "List supported conversions and pinned specifications.")
final class FormatsCommand implements Callable<Integer> {

  private final CliContext context;

  FormatsCommand(CliContext context) {
    this.context = context;
  }

  @Override
  public Integer call() {
    Converter converter = context.converter();
    context.out.println("Conversions:");
    converter.supportedConversions().stream()
        .sorted(
            Comparator.comparing((ConversionPair p) -> p.source().externalName())
                .thenComparing(p -> p.target().externalName()))
        .forEach(
            p ->
                context.out.println(
                    "  " + p.source().externalName() + " -> " + p.target().externalName()));
    context.out.println("Specifications:");
    converter
        .pinnedSpecifications()
        .forEach((format, spec) -> context.out.println("  " + format + ": " + spec));
    return CliMain.EXIT_OK;
  }
}
