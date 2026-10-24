package util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Web replacement for the console {@code InputValidator}. Instead of re-prompting,
 * it parses the text a user typed into a form field, checks type and range with
 * the same rules and messages, and collects one error message per field so the
 * form can be shown again with the problems highlighted.
 * <p>
 * Create one instance per form submission. Every method returns the parsed value,
 * or null (and records an error) when the value is not acceptable.
 */
public class FormValidator {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT); // rejects impossible dates such as 31-02-2026

    private final Map<String, String> errors = new LinkedHashMap<>();

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /** Field name -> message, in the order the fields were checked. */
    public Map<String, String> getErrors() {
        return errors;
    }

    /** Records an error that is not about the format of one value (e.g. "register number already exists"). */
    public void reject(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    // ---------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------

    /** A field that must not be empty. */
    public String requiredText(String field, String value, int maxLength) {
        String v = trim(value);
        if (v.isEmpty()) {
            reject(field, "This field cannot be empty.");
            return null;
        }
        return checkLength(field, v, maxLength);
    }

    /** A field that may be left blank; blank is stored as null. */
    public String optionalText(String field, String value, int maxLength) {
        String v = trim(value);
        return v.isEmpty() ? null : checkLength(field, v, maxLength);
    }

    /** Leaving the field empty keeps the current value. */
    public String textOrKeep(String field, String value, String current, int maxLength) {
        String v = trim(value);
        return v.isEmpty() ? current : checkLength(field, v, maxLength);
    }

    private String checkLength(String field, String v, int maxLength) {
        if (v.length() > maxLength) {
            reject(field, "Please use at most " + maxLength + " characters.");
            return null;
        }
        return v;
    }

    // ---------------------------------------------------------------
    // Whole numbers
    // ---------------------------------------------------------------

    /** A whole number between min and max (inclusive). */
    public Integer intInRange(String field, String value, int min, int max) {
        String v = trim(value);
        if (v.isEmpty()) {
            reject(field, "This field cannot be empty.");
            return null;
        }
        return parseInt(field, v, min, max);
    }

    /** Like {@link #intInRange} but leaving the field empty keeps the current value. */
    public Integer intInRangeOrKeep(String field, String value, int min, int max, int current) {
        String v = trim(value);
        return v.isEmpty() ? Integer.valueOf(current) : parseInt(field, v, min, max);
    }

    private Integer parseInt(String field, String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            if (n >= min && n <= max) {
                return n;
            }
            reject(field, rangeMessage(min, max));
        } catch (NumberFormatException e) {
            reject(field, "Please enter a valid whole number.");
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Decimal numbers
    // ---------------------------------------------------------------

    /** A decimal number between min and max (inclusive). NaN and infinity are rejected. */
    public Double decimalInRange(String field, String value, double min, double max) {
        String v = trim(value);
        if (v.isEmpty()) {
            reject(field, "This field cannot be empty.");
            return null;
        }
        return parseDouble(field, v, min, max);
    }

    /** Like {@link #decimalInRange} but leaving the field empty keeps the current value. */
    public Double decimalInRangeOrKeep(String field, String value, double min, double max, double current) {
        String v = trim(value);
        return v.isEmpty() ? Double.valueOf(current) : parseDouble(field, v, min, max);
    }

    private Double parseDouble(String field, String v, double min, double max) {
        try {
            double d = Double.parseDouble(v);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                reject(field, "Please enter a valid number.");
                return null;
            }
            if (d < min || d > max) {
                reject(field, rangeMessage(min, max));
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            reject(field, "Please enter a valid number.");
            return null;
        }
    }

    private String rangeMessage(double min, double max) {
        boolean noMin = min <= -Double.MAX_VALUE || min == Integer.MIN_VALUE;
        boolean noMax = max >= Double.MAX_VALUE || max == Integer.MAX_VALUE;
        if (noMin && noMax) {
            return "That value is not allowed.";
        }
        if (noMax) {
            return "Value must be at least " + trimNumber(min) + ".";
        }
        if (noMin) {
            return "Value must be at most " + trimNumber(max) + ".";
        }
        return "Value must be between " + trimNumber(min) + " and " + trimNumber(max) + ".";
    }

    private String trimNumber(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ---------------------------------------------------------------
    // Dates
    // ---------------------------------------------------------------

    /** A date as yyyy-MM-dd or dd-MM-yyyy. An empty field means today. */
    public LocalDate dateOrToday(String field, String value) {
        String v = trim(value);
        if (v.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(v); // ISO: 2026-11-05 (what the browser's date picker sends)
        } catch (DateTimeParseException ignored) {
            // try the day-month-year form next
        }
        try {
            return LocalDate.parse(v, DMY); // 05-11-2026
        } catch (DateTimeParseException e) {
            reject(field, "Please enter a valid date as yyyy-MM-dd or dd-MM-yyyy (or leave it empty for today).");
            return null;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
