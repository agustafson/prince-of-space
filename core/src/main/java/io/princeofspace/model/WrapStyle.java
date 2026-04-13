package io.princeofspace.model;

/**
 * Controls how the formatter distributes elements across lines when wrapping is triggered.
 *
 * <ul>
 *   <li>{@link #WIDE} — Keep as much on one line as possible; only wrap what's needed.
 *   <li>{@link #NARROW} — If any wrapping is needed, put one element per line.
 *   <li>{@link #BALANCED} — All-or-nothing: fits on one line, or each element gets its own line.
 * </ul>
 */
public enum WrapStyle {
    /** Keep as much on one line as possible; only wrap what is needed. */
    WIDE,
    /** Prefer one logical element per line when wrapping. */
    NARROW,
    /** Either fits on one line, or each wrapped element gets its own line. */
    BALANCED
}
