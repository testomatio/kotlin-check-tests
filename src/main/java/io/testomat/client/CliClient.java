package io.testomat.client;

import io.testomat.exception.CliException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

public class CliClient implements TestomatHttpClient {

    private static final String TEST_DATA_URL = "/api/test_data?api_key=";
    private static final String USER_AGENT = "Testomat-CLI/1.0";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String ACCEPT_JSON = "application/json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration GET_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POST_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int SUCCESS_STATUS_MIN = 200;
    private static final int SUCCESS_STATUS_MAX = 299;
    private static final int CLIENT_ERROR_MIN = 400;
    private static final int CLIENT_ERROR_MAX = 499;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String sendGetRequest(String apiKey, String serverUrl) {
        validateApiKey(apiKey);
        validateServerUrl(serverUrl);

        int attempt = 1;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverUrl + TEST_DATA_URL + apiKey))
                        .timeout(GET_REQUEST_TIMEOUT)
                        .header("Accept", ACCEPT_JSON)
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());

                validateGetResponse(response);
                return response.body();

            } catch (HttpTimeoutException e) {
                lastException = new CliException("Request timeout after "
                        + GET_REQUEST_TIMEOUT.getSeconds()
                        + " seconds: "
                        + e.getMessage(), e);
            } catch (ConnectException e) {
                lastException = new CliException("Cannot connect to " + hostOf(serverUrl)
                        + ". Check the server URL (--url / TESTOMATIO_URL) "
                        + "and your internet connection.", e);
            } catch (SocketTimeoutException e) {
                lastException = new CliException("Request timed out. The server might be busy.", e);
            } catch (IOException e) {
                lastException = new CliException("Network error occurred while sending request: "
                        + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CliException("Request was interrupted: " + e.getMessage(), e);
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CliException("Request was interrupted", ie);
                }
            }

            attempt++;
        }

        throw new CliException("Failed to fetch data after " + MAX_RETRIES + " attempts",
                lastException);
    }

    @Override
    public void sendPostRequest(String url, String jsonBody) {
        int attempt = 1;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", CONTENT_TYPE_JSON)
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .timeout(POST_REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (isSuccessfulResponse(response)) {
                    return;
                }

                String errorMessage = formatPostHttpError(response);

                if (isClientError(response)) {
                    throw new CliException(errorMessage);
                }

                lastException = new CliException(errorMessage);

            } catch (ConnectException e) {
                lastException = new CliException("Cannot connect to " + hostOf(url)
                        + ". Check the server URL (--url / TESTOMATIO_URL) "
                        + "and your internet connection.", e);
            } catch (SocketTimeoutException e) {
                lastException = new CliException("Request timed out. The server might be busy.", e);
            } catch (IOException | InterruptedException e) {
                lastException = new CliException("Network error occurred", e);
            }

            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new CliException("Export interrupted", ie);
                }
            }

            attempt++;
        }

        throw new CliException("Failed to send data after " + MAX_RETRIES + " attempts",
                lastException);
    }

    public static void validateServerUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new CliException("Server URL is required");
        }

        String url = serverUrl.trim();
        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new CliException("Invalid server URL: " + url
                    + ". Check --url / TESTOMATIO_URL", e);
        }

        if (uri.getHost() == null) {
            throw new CliException("Invalid server URL (missing host): " + url
                    + ". Check --url / TESTOMATIO_URL");
        }

        if (url.startsWith("https://")) {
            return;
        }

        if (url.startsWith("http://") && isLocalHost(url)) {
            return;
        }

        throw new CliException("Server URL must use HTTPS (got: " + url + ")");
    }

    private static String hostOf(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null ? uri.getHost() : url;
        } catch (Exception e) {
            return url;
        }
    }

    private static boolean isLocalHost(String url) {
        String host = url.substring("http://".length());

        int slash = host.indexOf('/');
        if (slash != -1) {
            host = host.substring(0, slash);
        }

        int colon = host.indexOf(':');
        if (colon != -1 && !host.startsWith("[")) {
            host = host.substring(0, colon);
        }

        return host.equals("localhost")
                || host.startsWith("127.0.0.1")
                || host.equals("::1")
                || host.startsWith("[::1]");
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty() || !apiKey.startsWith("tstmt_")) {
            throw new IllegalArgumentException("API key cannot be null or empty"
                    + " and should start with 'tstmt_'");
        }
    }

    private void validateGetResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();

        if (statusCode == 200) {
            return;
        }

        String errorMessage = buildGetErrorMessage(statusCode);
        throw new CliException(errorMessage + ". Response: " + response.body());
    }

    private String buildGetErrorMessage(int statusCode) {
        switch (statusCode) {
            case 401:
                return "401 Unauthorized: invalid API key. "
                        + "Check that the API key (--apikey / TESTOMATIO) is correct";
            case 403:
                return "403 Forbidden: invalid API key. "
                        + "Check the API key (--apikey / TESTOMATIO); if it is correct, "
                        + "verify the project permissions on testomat.io";
            case 404:
                return "404 Not Found: wrong server URL. "
                        + "Check --url / TESTOMATIO_URL";
            case 429:
                return "429 Too Many Requests: rate limit exceeded. Retry later";
            case 500:
                return "500 Internal Server Error: temporary server problem. Retry later";
            case 502:
            case 503:
            case 504:
                return "503 Service Unavailable: temporary server problem. Retry later";
            default:
                return "HTTP error " + statusCode;
        }
    }

    private boolean isSuccessfulResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        return statusCode >= SUCCESS_STATUS_MIN && statusCode <= SUCCESS_STATUS_MAX;
    }

    private boolean isClientError(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        return statusCode >= CLIENT_ERROR_MIN && statusCode <= CLIENT_ERROR_MAX;
    }

    private String formatPostHttpError(HttpResponse<String> response) {
        String body = response.body();
        int statusCode = response.statusCode();

        switch (statusCode) {
            case 401:
                return "401 Unauthorized: invalid API key. "
                        + "Check that the API key (--apikey / TESTOMATIO) is correct";
            case 403:
                return "403 Forbidden: invalid API key. "
                        + "Check the API key (--apikey / TESTOMATIO); if it is correct, "
                        + "verify the project upload permissions on testomat.io";
            case 404:
                return "404 Not Found: wrong server URL. "
                        + "Check --url / TESTOMATIO_URL";
            case 422:
                return "422 Invalid data: server rejected the payload."
                        + (body != null && !body.isEmpty() ? " Response: " + body : "");
            case 429:
                return "429 Too Many Requests: rate limit exceeded. Retry later";
            case 500:
                return "500 Internal Server Error: temporary server problem. Retry later";
            case 502:
            case 503:
            case 504:
                return "503 Service Unavailable: temporary server problem. Retry later";
            default:
                return "HTTP " + statusCode + ": "
                        + (body != null && !body.isEmpty() ? body : "Unknown error");
        }
    }
}
