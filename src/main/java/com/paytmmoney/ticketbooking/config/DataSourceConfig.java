package com.paytmmoney.ticketbooking.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Ensures any cloud provider database URL starting with postgresql:// or postgres://
 * is automatically prefixed with jdbc: before HikariCP and Flyway initialize.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    private final DataSourceProperties properties;

    public DataSourceConfig(DataSourceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void fixJdbcUrl() {
        String url = properties.getUrl();
        if (url != null && (url.startsWith("postgresql://") || url.startsWith("postgres://")) && !url.startsWith("jdbc:")) {
            String corrected = "jdbc:" + url;
            log.info("Auto-correcting cloud database URL: adding 'jdbc:' prefix -> {}", corrected.replaceAll(":[^/@]+@", ":****@"));
            properties.setUrl(corrected);
        }
    }
}
