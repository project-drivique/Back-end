package com.drivique.api;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class DatabaseHealthTestSupport {
    // Shared only within the test JVM; Ryuk removes this temporary database afterward.
    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    static {
        POSTGRES.start();
        applyLiquibaseMigrations();
    }

    private static void applyLiquibaseMigrations() {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Path externalDbRepo = Path.of("..", "database").toAbsolutePath().normalize();
            Path ddlChangelog = externalDbRepo.resolve("01_ddl").resolve("changelog.yaml");
            if (Files.exists(ddlChangelog)) {
                try (ResourceAccessor accessor = new DirectoryResourceAccessor(externalDbRepo)) {
                    new Liquibase("01_ddl/changelog.yaml", accessor, database).update("");
                }
            } else {
                try (ResourceAccessor accessor = new ClassLoaderResourceAccessor()) {
                    new Liquibase("db/changelog/db.changelog-test.yaml", accessor, database).update("");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron aplicar las migraciones Liquibase en el contenedor de pruebas", e);
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // Esquema base aplicado con Liquibase; Hibernate alinea y valida las entidades sobre el esquema migrado.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.create_namespaces", () -> "true");
        registry.add("spring.liquibase.enabled", () -> "false");
        // SMTP availability belongs to deployment monitoring, not database integration tests.
        registry.add("management.health.mail.enabled", () -> "false");
        // Secretos de prueba: en main/qa son obligatorios y nunca se usan valores publicados
        registry.add("jwt.secret", () -> "integration-test-signing-secret-0123456789-abcdefghij");
        registry.add("app.security.document-encryption-key", () -> "integration-test-documents-key-0123456789-abcdefghij");
        // El captcha real se prueba aparte; aqui no se llama a Cloudflare
        registry.add("app.captcha.enabled", () -> "false");
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
        registry.add("DB_HOST", POSTGRES::getHost);
        registry.add("DB_PORT", POSTGRES::getFirstMappedPort);
        registry.add("DB_NAME", POSTGRES::getDatabaseName);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Value("${local.server.port}") protected int port;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected HikariDataSource dataSource;

    protected void resetIamTables() {
        resetRentalTables();
        jdbc.execute("DELETE FROM billing.user_saved_payment_methods");
        jdbc.execute("DELETE FROM fleet.user_favorite_vehicles");
        jdbc.execute("DELETE FROM iam.user_social_accounts");
        jdbc.execute("DELETE FROM iam.user_consents");
        jdbc.execute("DELETE FROM iam.user_documents");
        jdbc.execute("DELETE FROM iam.user_preferences");
        jdbc.execute("DELETE FROM iam.verification_codes");
        jdbc.execute("DELETE FROM iam.user_sessions");
        jdbc.execute("DELETE FROM iam.user_roles");
        jdbc.execute("DELETE FROM iam.role_permissions");
        jdbc.execute("DELETE FROM location.branch_users");
        jdbc.execute("DELETE FROM iam.security_configuration_changes");
        jdbc.execute("DELETE FROM iam.users");
        jdbc.execute("DELETE FROM iam.roles");
        jdbc.execute("DELETE FROM iam.permissions");
        jdbc.execute("DELETE FROM iam.document_types");
        jdbc.execute("DELETE FROM iam.document_statuses");
    }

    protected void resetFleetTables() {
        resetRentalTables();
        jdbc.execute("DELETE FROM fleet.vehicle_maintenances");
        jdbc.execute("DELETE FROM fleet.maintenance_types");
        jdbc.execute("DELETE FROM fleet.user_favorite_vehicles");
        jdbc.execute("DELETE FROM fleet.vehicle_features");
        jdbc.execute("DELETE FROM fleet.features");
        jdbc.execute("DELETE FROM fleet.vehicle_documents");
        jdbc.execute("DELETE FROM fleet.vehicle_images");
        jdbc.execute("DELETE FROM fleet.vehicles");
        jdbc.execute("DELETE FROM fleet.vehicle_categories");
        jdbc.execute("DELETE FROM fleet.vehicle_brands");
        jdbc.execute("DELETE FROM fleet.transmission_types");
        jdbc.execute("DELETE FROM fleet.fuel_types");
        jdbc.execute("DELETE FROM fleet.vehicle_statuses");
    }

    protected void resetLocationTables() {
        resetRentalTables();
        jdbc.execute("DELETE FROM fleet.vehicle_maintenances");
        jdbc.execute("DELETE FROM fleet.maintenance_types");
        jdbc.execute("DELETE FROM fleet.user_favorite_vehicles");
        jdbc.execute("DELETE FROM fleet.vehicle_features");
        jdbc.execute("DELETE FROM fleet.vehicle_documents");
        jdbc.execute("DELETE FROM fleet.vehicle_images");
        jdbc.execute("DELETE FROM fleet.vehicles");
        jdbc.execute("DELETE FROM location.delivery_drivers");
        jdbc.execute("DELETE FROM location.branch_users");
        jdbc.execute("DELETE FROM location.branches");
        jdbc.execute("DELETE FROM location.cities");
        jdbc.execute("DELETE FROM location.departments");
    }

    /**
     * Un envío de notificaciones asíncrono puede seguir escribiendo intentos de entrega mientras se limpia la base:
     * se borra con DELETE (sin bloqueos exclusivos) y se reintenta si justo en ese momento llega una escritura.
     */
    private void clearNotificationTables() {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 15; attempt++) {
            try {
                jdbc.execute("DELETE FROM support.notification_delivery_attempts");
                jdbc.execute("DELETE FROM support.notification_outbox");
                return;
            } catch (org.springframework.dao.DataAccessException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    protected void resetRentalTables() {
        jdbc.execute("DELETE FROM audit.audit_logs");
        jdbc.execute("DELETE FROM audit.generated_reports");
        jdbc.execute("DELETE FROM audit.administrative_report_types");
        clearNotificationTables();
        jdbc.execute("DELETE FROM support.notifications");
        jdbc.execute("DELETE FROM support.incident_attachments");
        jdbc.execute("DELETE FROM support.incident_status_history");
        jdbc.execute("DELETE FROM support.incident_responses");
        jdbc.execute("DELETE FROM support.incident_reports");
        jdbc.execute("DELETE FROM rental.branch_reviews");
        jdbc.execute("DELETE FROM rental.vehicle_ratings");
        jdbc.execute("DELETE FROM billing.payment_receipts");
        jdbc.execute("DELETE FROM billing.payments");
        jdbc.execute("DELETE FROM contract.inspection_checklist_answers");
        jdbc.execute("DELETE FROM contract.vehicle_inspections");
        jdbc.execute("DELETE FROM contract.inspection_checklist_items");
        jdbc.execute("DELETE FROM contract.contract_clause_assignments");
        jdbc.execute("DELETE FROM contract.rental_contracts");
        jdbc.execute("DELETE FROM contract.contract_clauses");
        jdbc.execute("DELETE FROM contract.contract_statuses");
        jdbc.execute("DELETE FROM rental.rental_extension_requests");
        jdbc.execute("DELETE FROM rental.reservation_delivery_points");
        jdbc.execute("DELETE FROM rental.reservation_promotions");
        jdbc.execute("DELETE FROM rental.reservation_additional_services");
        jdbc.execute("DELETE FROM rental.reservations");
        jdbc.execute("DELETE FROM rental.reservation_statuses");
        jdbc.execute("DELETE FROM catalog.user_coupon_usages");
    }

    protected void resetCatalogTables() {
        jdbc.execute("DELETE FROM support.incident_attachments");
        jdbc.execute("DELETE FROM support.incident_status_history");
        jdbc.execute("DELETE FROM support.incident_responses");
        jdbc.execute("DELETE FROM support.incident_reports");
        jdbc.execute("DELETE FROM rental.branch_reviews");
        jdbc.execute("DELETE FROM rental.vehicle_ratings");
        jdbc.execute("DELETE FROM billing.payment_receipts");
        jdbc.execute("DELETE FROM billing.payments");
        jdbc.execute("DELETE FROM contract.inspection_checklist_answers");
        jdbc.execute("DELETE FROM contract.vehicle_inspections");
        jdbc.execute("DELETE FROM contract.inspection_checklist_items");
        jdbc.execute("DELETE FROM contract.contract_clause_assignments");
        jdbc.execute("DELETE FROM contract.rental_contracts");
        jdbc.execute("DELETE FROM contract.contract_clauses");
        jdbc.execute("DELETE FROM contract.contract_statuses");
        jdbc.execute("DELETE FROM rental.rental_extension_requests");
        jdbc.execute("DELETE FROM rental.reservation_delivery_points");
        jdbc.execute("DELETE FROM rental.reservation_promotions");
        jdbc.execute("DELETE FROM rental.reservation_additional_services");
        jdbc.execute("DELETE FROM rental.reservations");
        jdbc.execute("DELETE FROM catalog.user_coupon_usages");
        jdbc.execute("DELETE FROM catalog.promotions");
        jdbc.execute("DELETE FROM catalog.additional_services");
        jdbc.execute("DELETE FROM catalog.insurance_coverages");
        jdbc.execute("DELETE FROM catalog.mileage_plans");
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
    void connectsWithLiquibaseMigrationHistory() {
        assertThat(jdbc.queryForObject("select 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'databasechangelog'",
                Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from public.databasechangelog", Integer.class)).isPositive();
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
