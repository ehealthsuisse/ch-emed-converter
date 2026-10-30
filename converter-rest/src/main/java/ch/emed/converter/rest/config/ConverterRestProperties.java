package ch.emed.converter.rest.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Operator limits of the service, {@code converter.rest.*} (data-model §8, research R6).
 *
 * @param maxDocumentSize maximum size of the decoded input document
 * @param maxRequestSize maximum size of the whole request message (base64 adds about a third)
 * @param timeout time limit of one conversion
 * @param maxConcurrent conversions running at the same time; default: available processors
 * @param queueSize conversions waiting for a free slot; default: twice {@code maxConcurrent}
 */
@Validated
@ConfigurationProperties("converter.rest")
public record ConverterRestProperties(
    @DefaultValue("5MB") DataSize maxDocumentSize,
    @DefaultValue("8MB") DataSize maxRequestSize,
    @DefaultValue("20s") Duration timeout,
    @Min(1) Integer maxConcurrent,
    @Min(0) Integer queueSize) {

  public ConverterRestProperties {
    if (maxConcurrent == null) {
      maxConcurrent = Runtime.getRuntime().availableProcessors();
    }
    if (queueSize == null) {
      queueSize = 2 * maxConcurrent;
    }
  }

  @AssertTrue(message = "max-document-size must be > 0")
  boolean isMaxDocumentSizePositive() {
    return maxDocumentSize.toBytes() > 0;
  }

  @AssertTrue(message = "max-request-size must be >= 4/3 x max-document-size")
  boolean isMaxRequestSizeSufficient() {
    return maxRequestSize.toBytes() * 3 >= maxDocumentSize.toBytes() * 4;
  }

  @AssertTrue(message = "timeout must be > 0")
  boolean isTimeoutPositive() {
    return timeout.isPositive();
  }
}
