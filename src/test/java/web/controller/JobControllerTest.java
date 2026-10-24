package web.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How the eligible-departments field of a new job is stored. */
class JobControllerTest {

    @Test
    void departmentsAreUpperCasedTrimmedAndDeduplicated() {
        assertEquals("CSE,IT", JobController.normalizeDepartments(" cse, it ,CSE,, "));
    }

    @Test
    void allAnywhereMeansEveryDepartment() {
        assertEquals("ALL", JobController.normalizeDepartments("CSE, all"));
    }

    @Test
    void onlyCommasGivesEmpty() {
        assertEquals("", JobController.normalizeDepartments(" , ,"));
    }
}
