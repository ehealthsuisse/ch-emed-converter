package ch.emed.converter.rest.model;

/**
 * Request message {@code {document, options}} (data-model §2, research R2).
 *
 * @param document input document, base64-encoded (RFC 4648 §4); decoded bytes go to the library
 *     unchanged
 * @param options conversion options; null means library defaults
 */
public record ConversionRequestMessage(String document, RequestOptions options) {}
