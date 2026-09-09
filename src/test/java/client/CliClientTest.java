package client;

import com.sun.net.httpserver.HttpServer;
import io.testomat.client.CliClient;
import io.testomat.exception.CliException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliClientTest {

    private static final String KEY = "tstmt_test_key";
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldAllowHttpsServerUrl() {
        assertDoesNotThrow(() ->
            CliClient.validateServerUrl("https://app.testomat.io"));
    }

    @Test
    void shouldAllowLocalHttpServerUrl() {
        assertDoesNotThrow(() ->
            CliClient.validateServerUrl("http://localhost:8080"));
        assertDoesNotThrow(() ->
            CliClient.validateServerUrl("http://127.0.0.1"));
    }

    @Test
    void shouldRejectRemoteHttpServerUrl() {
        assertThrows(CliException.class, () ->
            CliClient.validateServerUrl("http://example.com"));
    }

    @Test
    void shouldRejectNullOrEmptyServerUrl() {
        assertThrows(CliException.class, () ->
            CliClient.validateServerUrl(null));
        assertThrows(CliException.class, () ->
            CliClient.validateServerUrl("   "));
    }

    @Test
    void shouldRejectMalformedServerUrl() {
        assertThrows(CliException.class, () ->
            CliClient.validateServerUrl("http://"));
        assertThrows(CliException.class, () ->
            CliClient.validateServerUrl("not-a-url"));
    }

    @Test
    void describeShouldIncludeConnectionClueFromCause() {
        CliException exception = new CliException(
            "Failed to fetch data after 3 attempts",
            new CliException("Cannot connect to localhost. "
                + "Check the server URL (--url / TESTOMATIO_URL)."));

        assertTrue(CliException.describe(exception).contains("Check the server URL"));
    }

    @Test
    void shouldRetryOnTransientServerError() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests =
            new java.util.concurrent.atomic.AtomicInteger();
        startServer(503, "unavailable", requests);
        CliClient client = new CliClient();

        assertThrows(CliException.class,
            () -> client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertEquals(3, requests.get());
    }

    @Test
    void shouldReturnBodyOnSuccessfulGet() throws Exception {
        startServer(200, "{\"tests\":{}}");
        CliClient client = new CliClient();

        String body = client.sendGetRequest(KEY, baseUrl());

        assertEquals("{\"tests\":{}}", body);
    }

    @Test
    void shouldThrow401MessageOnGet() throws Exception {
        startServer(401, "unauthorized");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendGetRequest(KEY, baseUrl()));

        assertTrue(exception.getMessage().contains("401"));
    }

    @Test
    void shouldThrow403MessageOnGet() throws Exception {
        startServer(403, "forbidden");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendGetRequest(KEY, baseUrl()));

        assertTrue(exception.getMessage().contains("403"));
        assertTrue(exception.getMessage().contains("invalid API key"));
    }

    @Test
    void shouldThrow404MessageOnGet() throws Exception {
        startServer(404, "not found");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendGetRequest(KEY, baseUrl()));

        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    void shouldThrow401MessageOnPost() throws Exception {
        startServer(401, "unauthorized");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertTrue(exception.getMessage().contains("401"));
        assertTrue(exception.getMessage().contains("invalid API key"));
    }

    @Test
    void shouldThrow403MessageOnPost() throws Exception {
        startServer(403, "forbidden");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertTrue(exception.getMessage().contains("403"));
        assertTrue(exception.getMessage().contains("invalid API key"));
    }

    @Test
    void shouldThrow404MessageOnPost() throws Exception {
        startServer(404, "not found");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertTrue(exception.getMessage().contains("404"));
        assertTrue(exception.getMessage().contains("server URL"));
    }

    @Test
    void shouldRetryOnImportLocked422() throws Exception {
        java.util.concurrent.atomic.AtomicInteger requests =
            new java.util.concurrent.atomic.AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            int number = requests.incrementAndGet();
            boolean locked = number <= 2;
            String body = locked
                    ? "[Import Locked] Please wait for the previous import to finish."
                    : "{\"ok\": true}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(locked ? 422 : 200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
        server.start();

        CliClient client = new CliClient();

        assertDoesNotThrow(() ->
            client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertEquals(3, requests.get());
    }

    @Test
    void shouldThrowImmediatelyOnOther422() throws Exception {
        startServer(422, "{\"error\":\"invalid data\"}");
        CliClient client = new CliClient();

        CliException exception = assertThrows(CliException.class,
            () -> client.sendPostRequest(baseUrl() + "/api/load?api_key=" + KEY, "{}"));

        assertTrue(exception.getMessage().contains("422"));
    }

    private String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void startServer(int status, String body) throws IOException {
        startServer(status, body, null);
    }

    private void startServer(int status, String body,
            java.util.concurrent.atomic.AtomicInteger requestCounter) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (requestCounter != null) {
                requestCounter.incrementAndGet();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
            exchange.close();
        });
        server.start();
    }
}
