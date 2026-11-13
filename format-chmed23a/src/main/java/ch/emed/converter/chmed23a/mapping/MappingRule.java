package ch.emed.converter.chmed23a.mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ids of the rules of {@code docs/mapping/chmed23a-ch-emed.md} (C23-*, M23-*) that a method or type
 * implements.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MappingRule {
  String[] value();
}
