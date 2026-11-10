package ch.emed.converter.epr;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ids of the mapping specification rules ({@code docs/mapping/ch-emed-epr.md}) a method or type
 * implements; checked by {@code MappingTraceabilityTest} of this module.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MappingRule {
  String[] value();
}
