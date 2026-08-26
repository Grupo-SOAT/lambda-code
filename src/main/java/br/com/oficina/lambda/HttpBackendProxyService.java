package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class HttpBackendProxyService implements BackendProxyService {

    private static final java.util.Set<String> HOP_BY_HOP_HEADERS = java.util.Set.of(
            "host", "content-length", "connection");

    private final HttpClient httpClient;
    private final String backendUrl;

    public HttpBackendProxyService(String backendUrl) {
        this(backendUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public HttpBackendProxyService(String backendUrl, HttpClient httpClient) {
        this.backendUrl = backendUrl.endsWith("/")
                ? backendUrl.substring(0, backendUrl.length() - 1)
                : backendUrl;
        this.httpClient = httpClient;
    }

    @Override
    public APIGatewayV2HTTPResponse forward(APIGatewayV2HTTPEvent event) {

        String path = event.getRawPath() == null ? "/" : event.getRawPath();
        String queryString = event.getRawQueryString();
        String uri = backendUrl + path + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);

        String method = event.getRequestContext().getHttp().getMethod();
        String body = event.getBody() == null ? "" : event.getBody();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.ofString(body));

        if (event.getHeaders() != null) {
            event.getHeaders().forEach((name, value) -> {
                if (name != null && value != null && !HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                    requestBuilder.header(name, value);
                }
            });
        }

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            Map<String, String> responseHeaders = new HashMap<>();
            response.headers().map().forEach((name, values) -> {
                if (!values.isEmpty()) {
                    responseHeaders.put(name, values.get(0));
                }
            });

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(response.statusCode())
                    .withHeaders(responseHeaders)
                    .withBody(response.body())
                    .build();

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Responses.error(502, "BACKEND_UNAVAILABLE", "Nao foi possivel contatar o backend");
        }
    }
}
