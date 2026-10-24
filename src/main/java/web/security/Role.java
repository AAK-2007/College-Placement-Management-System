package web.security;

/** Who is logged in. Spring Security sees these as ROLE_OFFICER, ROLE_STUDENT and ROLE_COMPANY. */
public enum Role {
    OFFICER,
    STUDENT,
    COMPANY
}
