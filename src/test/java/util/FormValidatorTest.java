package util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Validation rules: CGPA 0-10, backlogs >= 0, package 0-1000, dates, "empty keeps current value". */
class FormValidatorTest {

    @Test
    void cgpaMustBeBetweenZeroAndTen() {
        FormValidator v = new FormValidator();
        assertEquals(0.0, v.decimalInRange("cgpa", "0", 0, 10));
        assertEquals(10.0, v.decimalInRange("cgpa", "10", 0, 10));
        assertNull(v.decimalInRange("cgpa", "10.01", 0, 10));
        assertEquals("Value must be between 0 and 10.", v.getErrors().get("cgpa"));
    }

    @Test
    void rejectsTextNanAndInfinity() {
        for (String bad : new String[]{"abc", "NaN", "Infinity"}) {
            FormValidator v = new FormValidator();
            assertNull(v.decimalInRange("cgpa", bad, 0, 10), bad);
            assertEquals("Please enter a valid number.", v.getErrors().get("cgpa"), bad);
        }
    }

    @Test
    void backlogsCannotBeNegativeOrDecimal() {
        FormValidator v = new FormValidator();
        assertNull(v.intInRange("backlogs", "-1", 0, Integer.MAX_VALUE));
        assertEquals("Value must be at least 0.", v.getErrors().get("backlogs"));

        FormValidator v2 = new FormValidator();
        assertNull(v2.intInRange("backlogs", "1.5", 0, Integer.MAX_VALUE));
        assertEquals("Please enter a valid whole number.", v2.getErrors().get("backlogs"));
    }

    @Test
    void packageMustBeBetweenZeroAndThousand() {
        FormValidator v = new FormValidator();
        assertEquals(1000.0, v.decimalInRange("packageLpa", "1000", 0, 1000));
        assertNull(v.decimalInRange("packageLpa", "1000.5", 0, 1000));
        assertTrue(v.hasErrors());
    }

    @Test
    void requiredTextCannotBeBlankAndRespectsLength() {
        FormValidator v = new FormValidator();
        assertNull(v.requiredText("name", "   ", 100));
        assertNull(v.requiredText("phone", "123456789012345678901", 20));
        assertEquals("This field cannot be empty.", v.getErrors().get("name"));
        assertEquals("Please use at most 20 characters.", v.getErrors().get("phone"));
    }

    @Test
    void optionalTextBlankBecomesNull() {
        FormValidator v = new FormValidator();
        assertNull(v.optionalText("email", "  ", 100));
        assertEquals("a@b.com", v.optionalText("email", " a@b.com ", 100));
        assertFalse(v.hasErrors());
    }

    @Test
    void emptyFieldKeepsCurrentValueOnEdit() {
        FormValidator v = new FormValidator();
        assertEquals("Old", v.textOrKeep("name", "", "Old", 100));
        assertEquals(7.5, v.decimalInRangeOrKeep("cgpa", " ", 0, 10, 7.5));
        assertEquals(2, v.intInRangeOrKeep("backlogs", null, 0, Integer.MAX_VALUE, 2));
        assertEquals(8.0, v.decimalInRangeOrKeep("cgpa", "8", 0, 10, 7.5));
        assertFalse(v.hasErrors());
    }

    @Test
    void datesAcceptBothFormatsAndDefaultToToday() {
        FormValidator v = new FormValidator();
        assertEquals(LocalDate.of(2026, 11, 5), v.dateOrToday("date", "2026-11-05"));
        assertEquals(LocalDate.of(2026, 11, 5), v.dateOrToday("date", "05-11-2026"));
        assertEquals(LocalDate.now(), v.dateOrToday("date", ""));
        assertFalse(v.hasErrors());
    }

    @Test
    void impossibleDatesAreRejected() {
        FormValidator v = new FormValidator();
        assertNull(v.dateOrToday("date", "31-02-2026"));
        assertTrue(v.getErrors().get("date").startsWith("Please enter a valid date"));
    }
}
