package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpBackendProxyServiceTest {

    private HttpServer server;
    private String backendUrl;
    private final AtomicReference<String> lastRequestPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();

    @BeforeEach
    void startFakeBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            lastRequestPath.set(exchange.getRequestURI().toString());
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));

            byte[] response = "{\"ok\":true}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        backendUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeBackend() {
        server.stop(0);
    }

    @Test
    void forwardsMethodPathQueryAndHeadersToBackend() {

        HttpBackendProxyService proxy = new HttpBackendProxyService(backendUrl);

        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withRawPath("/owners/1")
                .withRawQueryString("page=1")
                .withBody("")
                .withHeaders(Map.of("Authorization", "Bearer some-token"))
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod("GET")
                                .build())
                        .build())
                .build();

        APIGatewayV2HTTPResponse response = proxy.forward(event);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"ok\":true"));
        assertEquals("/owners/1?page=1", lastRequestPath.get());
        assertEquals("Bearer some-token", lastAuthHeader.get());
    }
}
