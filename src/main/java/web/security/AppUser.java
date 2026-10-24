package web.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The logged-in user, kept in the session by Spring Security.
 * A student account carries its studentId and a company account its companyId,
 * so every page can limit what it shows to that student's or company's own data.
 */
public class AppUser implements UserDetails {

    private final String username;
    private final String passwordHash;
    private final Role role;
    private final Integer studentId;
    private final Integer companyId;
    private final String displayName;

    public AppUser(String username, String passwordHash, Role role,
                   Integer studentId, Integer companyId, String displayName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.studentId = studentId;
        this.companyId = companyId;
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isOfficer() {
        return role == Role.OFFICER;
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public boolean isCompany() {
        return role == Role.COMPANY;
    }

    /** The linked student's id (student accounts only, otherwise null). */
    public Integer getStudentId() {
        return studentId;
    }

    /** The linked company's id (company accounts only, otherwise null). */
    public Integer getCompanyId() {
        return companyId;
    }

    /** Name shown in the header: the student's or company's name, or "Placement Officer". */
    public String getDisplayName() {
        return displayName;
    }

    /** Role shown in the header, e.g. "Student". */
    public String getRoleLabel() {
        return switch (role) {
            case OFFICER -> "Placement Officer";
            case STUDENT -> "Student";
            case COMPANY -> "Company";
        };
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
