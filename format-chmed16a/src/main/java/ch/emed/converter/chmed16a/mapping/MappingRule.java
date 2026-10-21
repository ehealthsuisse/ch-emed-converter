package ch.emed.converter.chmed16a.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ids of the mapping specification rules ({@code docs/mapping/chmed16a-ch-emed.md}) a method
 * implements.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MappingRule {
  String[] value();
}
