package web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

/**
 * Turns errors into a friendly page. The full details go to the server log;
 * the browser never sees a stack trace or raw SQL error text.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A bad id in the address bar, e.g. /students/abc/edit. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String badParameter(MethodArgumentTypeMismatchException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("message", "That link is not valid. Please use the menu to find the record.");
        return "error";
    }

    /** An ownership check failed, e.g. a company opening another company's application. */
    @ExceptionHandler(AccessDeniedException.class)
    public String accessDenied(AccessDeniedException e, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("message", "You do not have access to that page.");
        return "error";
    }

    /** DAO failures (they wrap SQLException in RuntimeException) and anything else unexpected. */
    @ExceptionHandler(RuntimeException.class)
    public String unexpected(RuntimeException e, Model model, HttpServletResponse response) {
        log.error("Request failed", e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        model.addAttribute("message", friendlyMessage(e));
        return "error";
    }

    /** Plain-language explanation of a database (or other) failure, safe to show in the browser. */
    public static String friendlyMessage(Throwable e) {
        boolean sqlProblem = false;
        boolean connectionProblem = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException se) {
                sqlProblem = true;
                // Most specific causes first: they sit at the bottom of the chain.
                if ("28000".equals(se.getSQLState())) {
                    return "MySQL rejected the user name or password. Check the DB_USER and DB_PASSWORD "
                            + "environment variables, then restart the application.";
                }
                if (se.getErrorCode() == 1049) {
                    return "The placement_system database does not exist yet. Run schema.sql in MySQL first.";
                }
            }
            if (t instanceof SQLTransientConnectionException
                    || "Unable to connect to the database.".equals(t.getMessage())
                    || t.getClass().getName().contains("CommunicationsException")) {
                connectionProblem = true;
            }
        }
        if (connectionProblem) {
            return "Could not connect to the database. Check that MySQL is running and that "
                    + "DB_USER / DB_PASSWORD are set correctly, then try again.";
        }
        return sqlProblem
                ? "A database error occurred, so the action was not completed. Please try again."
                : "Something went wrong, so the action was not completed. Please try again.";
    }
}
