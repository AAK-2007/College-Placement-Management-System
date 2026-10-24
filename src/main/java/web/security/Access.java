package web.security;

import dao.ApplicationDAO.ApplicationView;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/**
 * Ownership checks used by the controllers, on top of the page rules in SecurityConfig:
 * a student may only touch their own records and a company only those for its own jobs.
 * A failed check throws AccessDeniedException, which shows the "not allowed" page.
 */
public final class Access {

    private Access() {
    }

    public static AccessDeniedException denied() {
        return new AccessDeniedException("Not allowed");
    }

    /** Officer: any application. Student: their own. Company: applications to its own jobs. */
    public static void requireOwnApplication(AppUser me, ApplicationView application) {
        if (me.isOfficer()) {
            return;
        }
        if (me.isStudent() && Objects.equals(me.getStudentId(), application.studentId())) {
            return;
        }
        if (me.isCompany() && Objects.equals(me.getCompanyId(), application.companyId())) {
            return;
        }
        throw denied();
    }
}
