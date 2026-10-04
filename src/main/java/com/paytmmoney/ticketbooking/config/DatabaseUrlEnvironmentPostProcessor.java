package com.paytmmoney.ticketbooking.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Normalizes cloud provider database URLs (Render, Railway, Fly.io, Heroku).
 * Converts postgres:// or postgresql:// into jdbc:postgresql:// and extracts credentials.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        }

        if (dbUrl != null && (dbUrl.startsWith("postgres://") || dbUrl.startsWith("postgresql://")) && !dbUrl.startsWith("jdbc:")) {
            try {
                // Replace scheme to parse with java.net.URI
                String parseable = dbUrl.replaceFirst("^postgres(ql)?://", "http://");
                URI uri = new URI(parseable);

                String userInfo = uri.getUserInfo();
                String host = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 5432;
                String path = uri.getPath(); // /dbname

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + (path != null ? path : "");
                Map<String, Object> props = new HashMap<>();
                props.put("spring.datasource.url", jdbcUrl);

                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    props.put("spring.datasource.username", parts[0]);
                    props.put("spring.datasource.password", parts[1]);
                }

                environment.getPropertySources().addFirst(new MapPropertySource("cloudDatabaseUrl", props));
            } catch (Exception ignored) {
            }
        }
    }
}
