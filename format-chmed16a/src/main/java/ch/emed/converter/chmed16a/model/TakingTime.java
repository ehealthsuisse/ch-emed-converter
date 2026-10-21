package ch.emed.converter.chmed16a.model;

import java.math.BigDecimal;

/**
 * CHMED16A Taking Time (medication plans only).
 *
 * @param off offset in seconds after cycle start (cycles start at DtFrom 00:00)
 * @param du duration of the application in seconds (default 0)
 * @param doFrom dose from (required if not in reserve)
 * @param doTo dose to (default doFrom)
 * @param a amount (required if in reserve)
 * @param ma maximum amount per cycle
 */
public record TakingTime(
    Long off, Long du, BigDecimal doFrom, BigDecimal doTo, BigDecimal a, BigDecimal ma) {}
