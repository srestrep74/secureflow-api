package com.secureflow;

import com.secureflow.identity.infrastructure.AdministratorBootstrapService;
import com.secureflow.shared.domain.UserId;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class SecureFlowApplication {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("bootstrap-admin")) {
            int result = bootstrap(args);
            if (result != 0) System.exit(result);
            return;
        }
        SpringApplication.run(SecureFlowApplication.class, args);
    }

    static int bootstrap(String[] args) {
        if (args.length != 2
                || !args[1].matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            System.err.println("Usage: bootstrap-admin <user-uuid>");
            return 2;
        }
        try (var context = new SpringApplicationBuilder(SecureFlowApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.main.banner-mode",
                        "off",
                        "spring.main.log-startup-info",
                        "false",
                        "logging.level.root",
                        "OFF"))
                .run()) {
            var result =
                    context.getBean(AdministratorBootstrapService.class).execute(new UserId(UUID.fromString(args[1])));
            System.out.println(
                    result == com.secureflow.identity.application.AdministratorBootstrap.Outcome.PROMOTED
                            ? "Administrator bootstrap completed."
                            : "Administrator bootstrap already completed for this user.");
            return 0;
        } catch (RuntimeException e) {
            System.err.println(
                    "Administrator bootstrap failed; check eligibility, existing administrators and database availability.");
            return 1;
        }
    }
}
