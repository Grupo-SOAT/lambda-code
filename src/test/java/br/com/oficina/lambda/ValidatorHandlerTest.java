package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidatorHandlerTest {

    @Mock
    private OwnerRepository ownerRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private Context context;

    private ValidatorHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(new NoOpLambdaLogger());
        handler = new ValidatorHandler(ownerRepository, jwtService);
    }

    @Test
    void issuesTokenForKnownOwner() throws SQLException {

        when(ownerRepository.findByDocument("84779441056"))
                .thenReturn(Optional.of(new Owner(1L, "Joao Silva", "84779441056", "joao@example.com")));
        when(jwtService.issueClienteToken("84779441056", 1L)).thenReturn("signed-jwt");

        APIGatewayV2HTTPResponse response = handler.handleRequest(
                authRequest("{\"cpf\":\"847.794.410-56\"}"), context);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("signed-jwt"));
        assertTrue(response.getBody().contains("Joao Silva"));
    }

    @Test
    void returns404WhenOwnerNotFound() throws SQLException {

        when(ownerRepository.findByDocument("39544511075")).thenReturn(Optional.empty());

        APIGatewayV2HTTPResponse response = handler.handleRequest(
                authRequest("{\"cpf\":\"39544511075\"}"), context);

        assertEquals(404, response.getStatusCode());
        assertTrue(response.getBody().contains("CLIENT_NOT_FOUND"));
    }

    @Test
    void returns400ForInvalidCpfFormat() {

        APIGatewayV2HTTPResponse response = handler.handleRequest(
                authRequest("{\"cpf\":\"11111111111\"}"), context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_CPF"));
    }

    @Test
    void returns400WhenCpfMissing() {

        APIGatewayV2HTTPResponse response = handler.handleRequest(authRequest("{}"), context);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void returns500WhenDatabaseFails() throws SQLException {

        when(ownerRepository.findByDocument(any())).thenThrow(new SQLException("connection refused"));

        APIGatewayV2HTTPResponse response = handler.handleRequest(
                authRequest("{\"cpf\":\"84779441056\"}"), context);

        assertEquals(500, response.getStatusCode());
    }

    @Test
    void returns404ForNonAuthRoutes() {

        APIGatewayV2HTTPResponse response = handler.handleRequest(requestFor("GET", "/owners/1", null), context);

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void returns404ForGetOnAuthPath() {

        APIGatewayV2HTTPResponse response = handler.handleRequest(requestFor("GET", "/auth/cpf", null), context);

        assertEquals(404, response.getStatusCode());
    }

    private static APIGatewayV2HTTPEvent authRequest(String body) {
        return requestFor("POST", "/auth/cpf", body);
    }

    private static APIGatewayV2HTTPEvent requestFor(String method, String path, String body) {
        return APIGatewayV2HTTPEvent.builder()
                .withRawPath(path)
                .withBody(body)
                .withHeaders(Map.of("content-type", "application/json"))
                .withRequestContext(APIGatewayV2HTTPEvent.RequestContext.builder()
                        .withHttp(APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                                .withMethod(method)
                                .withPath(path)
                                .build())
                        .build())
                .build();
    }

    private static class NoOpLambdaLogger implements LambdaLogger {
        @Override
        public void log(String message) {
        }

        @Override
        public void log(byte[] message) {
        }
    }
}
