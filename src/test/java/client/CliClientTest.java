package client;

import io.testomat.client.CliClient;
import io.testomat.exception.CliException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliClientTest {

    @Test
    void shouldAllowHttpsServerUrl() {
        assertDoesNotThrow(() ->
            CliClient.validateServerUrl("https://testomat.io"));
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
}
