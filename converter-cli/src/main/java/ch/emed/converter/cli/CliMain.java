package ch.emed.converter.cli;

import ch.emed.converter.core.api.Converter;
import ch.emed.converter.core.api.Converters;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command-line entry point (contracts/cli.md). Exit codes: 0 success (with or without warnings), 1
 * conversion failed, 2 usage error, 3 internal error.
 */
@Command(
    name = "ch-emed-converter",
    description = "Converts Swiss eMedication documents to and from CH EMED.",
    subcommands = {ConvertCommand.class, FormatsCommand.class})
public final class CliMain implements Runnable {

  public static final int EXIT_OK = 0;
  public static final int EXIT_FAILED = 1;
  public static final int EXIT_USAGE = 2;
  public static final int EXIT_INTERNAL = 3;

  private final CliContext context;

  @Option(names = "--version", description = "Print converter and specification versions.")
  boolean version;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Show this help.")
  boolean help;

  CliMain(CliContext context) {
    this.context = context;
  }

  @Override
  public void run() {
    if (version) {
      Converter converter = context.converter();
      context.out.println("ch-emed-converter " + converter.version());
      converter
          .pinnedSpecifications()
          .forEach((format, spec) -> context.out.println(format + ": " + spec));
    } else {
      new CommandLine(this).usage(context.out);
    }
  }

  public static void main(String[] args) {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
    System.exit(execute(Converters::create, args, System.in, out, err));
  }

  /**
   * Runs the CLI.
   *
   * @param converter supplies the converter (created lazily; building it is expensive)
   * @param args arguments
   * @param in standard input
   * @param out standard output (output documents only)
   * @param err standard error (report and diagnostics)
   * @return exit code
   */
  public static int execute(
      Supplier<Converter> converter,
      String[] args,
      InputStream in,
      PrintStream out,
      PrintStream err) {
    CliContext context = new CliContext(converter, in, out, err);
    CommandLine.IFactory factory =
        new CommandLine.IFactory() {
          @Override
          public <K> K create(Class<K> cls) throws Exception {
            if (cls == CliMain.class) {
              return cls.cast(new CliMain(context));
            }
            if (cls == ConvertCommand.class) {
              return cls.cast(new ConvertCommand(context));
            }
            if (cls == FormatsCommand.class) {
              return cls.cast(new FormatsCommand(context));
            }
            return CommandLine.defaultFactory().create(cls);
          }
        };
    CommandLine cli = new CommandLine(CliMain.class, factory);
    cli.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
    cli.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
    cli.setParameterExceptionHandler(
        (ex, a) -> {
          err.println("Usage error: " + ex.getMessage());
          return EXIT_USAGE;
        });
    cli.setExecutionExceptionHandler(
        (ex, commandLine, parseResult) -> {
          if (ex instanceof CommandLine.ParameterException pe) {
            err.println("Usage error: " + pe.getMessage());
            return EXIT_USAGE;
          }
          err.println("Internal error: " + ex.getClass().getSimpleName());
          return EXIT_INTERNAL;
        });
    return cli.execute(args);
  }
}
