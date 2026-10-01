package com.drivique.api;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class DatabaseHealthTestSupport {
    // Shared only within the test JVM; Ryuk removes this temporary database afterward.
    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // Test-only schema generated from mappings; production retains ddl-auto=validate.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Value("${local.server.port}") protected int port;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected HikariDataSource dataSource;

    protected void resetIamTables() {
        jdbc.execute("DELETE FROM iam.user_consents");
        jdbc.execute("DELETE FROM iam.user_documents");
        jdbc.execute("DELETE FROM iam.user_preferences");
        jdbc.execute("DELETE FROM iam.verification_codes");
        jdbc.execute("DELETE FROM iam.user_sessions");
        jdbc.execute("DELETE FROM iam.user_roles");
        jdbc.execute("DELETE FROM iam.role_permissions");
        jdbc.execute("DELETE FROM iam.users");
        jdbc.execute("DELETE FROM iam.roles");
        jdbc.execute("DELETE FROM iam.permissions");
        jdbc.execute("DELETE FROM iam.document_types");
        jdbc.execute("DELETE FROM iam.document_statuses");
    }

    protected void resetFleetTables() {
        jdbc.execute("DELETE FROM fleet.vehicles");
        jdbc.execute("DELETE FROM fleet.vehicle_categories");
        jdbc.execute("DELETE FROM fleet.vehicle_brands");
        jdbc.execute("DELETE FROM fleet.transmission_types");
        jdbc.execute("DELETE FROM fleet.fuel_types");
        jdbc.execute("DELETE FROM fleet.vehicle_statuses");
    }

    protected void resetLocationTables() {
        jdbc.execute("DELETE FROM fleet.vehicles");
        jdbc.execute("DELETE FROM location.branch_users");
        jdbc.execute("DELETE FROM location.branches");
        jdbc.execute("DELETE FROM location.cities");
        jdbc.execute("DELETE FROM location.departments");
    }

    @Test
    void healthReportsDatabaseUpWithoutConnectionDetails() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"", "\"db\":{\"status\":\"UP\"}")
                .doesNotContain("jdbc:", "password", "validationQuery", "\"details\"");
    }

    @Test
    void infoExposesOnlyPublicApplicationMetadata() throws Exception {
        var response = get("/actuator/info");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Drivique API", "\"version\":\"0.0.1-SNAPSHOT\"")
                .doesNotContain("@project.version@", "jdbc:", "password", "username");
    }

    @Test
    void connectsWithoutMigrationHistory() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('databasechangelog', 'flyway_schema_history')", Integer.class)).isZero();
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(2);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(30000);
    }

    @Test
    void managementConfigurationDoesNotExposeEnvironment() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isIn(401, 403, 404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
