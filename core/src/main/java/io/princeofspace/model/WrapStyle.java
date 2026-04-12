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
    WIDE,
    NARROW,
    BALANCED
}
