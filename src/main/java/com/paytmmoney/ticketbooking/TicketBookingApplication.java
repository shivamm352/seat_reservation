package com.paytmmoney.ticketbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Paytm Money Ticket Booking application.
 *
 * <p>Architecture overview:
 * <ul>
 *   <li>Spring Boot 3.3 on Java 17</li>
 *   <li>PostgreSQL 16 with HikariCP connection pooling</li>
 *   <li>Flyway for schema migrations</li>
 *   <li>JWT-based stateless authentication</li>
 *   <li>Prometheus metrics via Micrometer</li>
 * </ul>
 */
@SpringBootApplication
public class TicketBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApplication.class, args);
    }
}
