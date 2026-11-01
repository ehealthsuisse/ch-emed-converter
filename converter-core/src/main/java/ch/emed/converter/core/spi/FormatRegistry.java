package ch.emed.converter.core.spi;

import ch.emed.converter.core.api.FormatId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** Registry of format modules, discovered via {@link ServiceLoader} or given explicitly. */
public final class FormatRegistry {

  private final Map<FormatId, FormatModule> modules;

  private FormatRegistry(Map<FormatId, FormatModule> modules) {
    this.modules = modules;
  }

  public static FormatRegistry load() {
    return of(
        ServiceLoader.load(FormatModule.class).stream().map(ServiceLoader.Provider::get).toList());
  }

  public static FormatRegistry of(Collection<FormatModule> modules) {
    Map<FormatId, FormatModule> byFormat = new LinkedHashMap<>();
    for (FormatModule module : modules) {
      if (module.format().isChEmed()) {
        throw new IllegalStateException("CH_EMED is the hub model and has no format module");
      }
      if (byFormat.putIfAbsent(module.format(), module) != null) {
        throw new IllegalStateException("Duplicate format module for " + module.format().id());
      }
    }
    return new FormatRegistry(Map.copyOf(byFormat));
  }

  public Optional<FormatModule> find(FormatId format) {
    return Optional.ofNullable(modules.get(format));
  }

  public List<FormatModule> modules() {
    return modules.values().stream()
        .sorted(java.util.Comparator.comparing(m -> m.format().id()))
        .toList();
  }
}
