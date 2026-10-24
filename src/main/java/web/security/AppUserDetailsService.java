package web.security;

import dao.UserDAO;
import dao.UserDAO.UserRecord;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Finds the account for a login attempt: the placement officer (from application.properties)
 * or a student / company account (from the users table).
 */
public class AppUserDetailsService implements UserDetailsService {

    private final String officerUsername;
    private final String officerPasswordHash;
    private final UserDAO userDAO;

    public AppUserDetailsService(String officerUsername, String officerPasswordHash, UserDAO userDAO) {
        this.officerUsername = officerUsername;
        this.officerPasswordHash = officerPasswordHash;
        this.userDAO = userDAO;
    }

    /** The officer's user name cannot be taken by a student or company account. */
    public boolean isOfficerUsername(String username) {
        return username != null && username.trim().equalsIgnoreCase(officerUsername);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (isOfficerUsername(username)) {
            return new AppUser(officerUsername, officerPasswordHash, Role.OFFICER, null, null, "Placement Officer");
        }
        UserRecord r = userDAO.findByUsername(username);
        if (r == null) {
            throw new UsernameNotFoundException("No such user");
        }
        Role role = Role.valueOf(r.role());
        return new AppUser(r.username(), r.passwordHash(), role, r.studentId(), r.companyId(), r.displayName());
    }
}
