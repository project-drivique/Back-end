package com.drivique.api.integration;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class FrankfurterExchangeRateProviderTests {
    HttpServer server;
    ExecutorService executor;
    FrankfurterExchangeRateProvider provider;
    volatile int status;
    volatile String response;
    volatile long delay;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        status = 200;
        response = null;
        delay = 0;
        server.createContext("/v2/rates", exchange -> {
            try {
                Thread.sleep(delay);
                String body = response;
                if (body == null) {
                    boolean cop = exchange.getRequestURI().getQuery().contains("base=COP");
                    body = cop
                        ? "[{\"date\":\"2026-01-01\",\"base\":\"COP\",\"quote\":\"USD\",\"rate\":0.000303237}]"
                        : "[{\"date\":\"2026-01-01\",\"base\":\"USD\",\"quote\":\"COP\",\"rate\":3297.75}]";
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        provider = new FrankfurterExchangeRateProvider(
                "http://127.0.0.1:" + server.getAddress().getPort(), 250);
    }

    @AfterEach
    void cleanup() { server.stop(0); executor.shutdownNow(); }

    @Test
    void readsAllActivePairsWithDecimalPrecisionAndCollectionTime() {
        var rates = provider.fetch(List.of("COP", "USD"));
        assertThat(rates).hasSize(2);
        assertThat(rates.getFirst().rate()).isEqualByComparingTo(new BigDecimal("0.000303237"));
        assertThat(rates.getFirst().fetchedAt()).isEqualTo(rates.getLast().fetchedAt());
        assertThat(provider.name()).isEqualTo("Frankfurter v2");
    }

    @Test
    void rejectsMissingPairs() {
        response = "[]";
        assertThatThrownBy(() -> provider.fetch(List.of("COP", "USD")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnexpectedCurrency() {
        response = "[{\"date\":\"2026-01-01\",\"base\":\"COP\",\"quote\":\"GBP\",\"rate\":1}]";
        assertThatThrownBy(() -> provider.fetch(List.of("COP", "USD")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsProviderErrorsAndMalformedJson() {
        status = 429;
        response = "{}";
        assertThatThrownBy(() -> provider.fetch(List.of("COP", "USD")))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
        status = 200;
        response = "invalid json";
        assertThatThrownBy(() -> provider.fetch(List.of("COP", "USD")))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Test
    void boundsWaitingForProvider() {
        delay = 1000;
        assertThatThrownBy(() -> provider.fetch(List.of("COP", "USD")))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }
}
