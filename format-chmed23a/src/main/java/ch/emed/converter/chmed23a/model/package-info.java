/**
 * CHMED23A object model (README.md and posology.md of the pinned specification, data-model §2).
 * Immutable records; property names are those of the JSON; absent values are null, absent lists are
 * empty. Polymorphic objects are sealed interfaces with one record per object type {@code t}.
 */
package ch.emed.converter.chmed23a.model;
