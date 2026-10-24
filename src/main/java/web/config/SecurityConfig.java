package web.config;

import dao.UserDAO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import web.security.AppUserDetailsService;

/**
 * Who may open which page.
 * <ul>
 *   <li>Placement officer: everything.</li>
 *   <li>Student: own profile, companies and jobs (read only), own eligibility, apply, own applications.</li>
 *   <li>Company: own jobs (list and post), applications to own jobs (shortlist), interviews for them.</li>
 * </ul>
 * These rules decide which pages each role can open; the controllers additionally check that a
 * student or company only ever sees or changes its own records.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Public: home page, login, sign-up, styles, error page
                        .requestMatchers("/", "/login", "/register/**", "/css/**", "/error", "/error/**").permitAll()

                        // Students: officer manages all; a student only their own profile
                        .requestMatchers("/students/me", "/students/me/**").hasRole("STUDENT")
                        .requestMatchers("/students", "/students/**").hasRole("OFFICER")

                        // Companies: students may view the list; only the officer adds or deletes
                        .requestMatchers(HttpMethod.GET, "/companies").hasAnyRole("OFFICER", "STUDENT")
                        .requestMatchers("/companies", "/companies/**").hasRole("OFFICER")

                        // Jobs: everyone logged in can list (companies see only their own); officer and companies post
                        .requestMatchers("/jobs/add").hasAnyRole("OFFICER", "COMPANY")
                        .requestMatchers("/jobs", "/jobs/**").hasAnyRole("OFFICER", "STUDENT", "COMPANY")

                        // Eligibility: officer sees all; a student only their own
                        .requestMatchers("/eligibility/job").hasRole("OFFICER")
                        .requestMatchers("/eligibility", "/eligibility/**").hasAnyRole("OFFICER", "STUDENT")

                        // Applications: students apply; officer and companies shortlist; lists are filtered per role
                        .requestMatchers("/applications/apply").hasAnyRole("OFFICER", "STUDENT")
                        .requestMatchers("/applications/*/shortlist").hasAnyRole("OFFICER", "COMPANY")
                        .requestMatchers("/applications", "/applications/**").hasAnyRole("OFFICER", "STUDENT", "COMPANY")

                        // Interviews: officer and companies (companies only for their own jobs)
                        .requestMatchers("/interviews", "/interviews/**").hasAnyRole("OFFICER", "COMPANY")

                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        // A database problem during login is not a wrong password: say so.
                        .failureHandler((request, response, ex) -> response.sendRedirect(
                                ex instanceof InternalAuthenticationServiceException ? "/login?unavailable" : "/login?error"))
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(e -> e.accessDeniedPage("/error/403"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** The officer account comes from application.properties; student and company accounts from the users table. */
    @Bean
    public AppUserDetailsService userDetailsService(@Value("${app.officer.username}") String officerUsername,
                                                    @Value("${app.officer.password-hash}") String officerPasswordHash,
                                                    UserDAO userDAO) {
        return new AppUserDetailsService(officerUsername, officerPasswordHash, userDAO);
    }
}
