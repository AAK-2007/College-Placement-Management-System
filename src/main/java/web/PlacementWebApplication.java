package web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Starts the web application on http://localhost:8080.
 * <p>
 * Extra mode: run with {@code --hash-password=YourNewPassword} to print a BCrypt
 * hash for the officer password and exit, without starting the server.
 */
@SpringBootApplication
public class PlacementWebApplication {

    private static final String HASH_ARG = "--hash-password=";

    public static void main(String[] args) {
        for (String arg : args) {
            if (arg.startsWith(HASH_ARG)) {
                String plain = arg.substring(HASH_ARG.length());
                if (plain.isBlank()) {
                    System.out.println("Usage: --hash-password=YourNewPassword");
                } else {
                    System.out.println("OFFICER_PASSWORD_HASH=" + new BCryptPasswordEncoder().encode(plain));
                }
                return;
            }
        }
        SpringApplication.run(PlacementWebApplication.class, args);
    }
}
