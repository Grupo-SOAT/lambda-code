package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthorizerHandlerTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-characters!!";

    @Mock
    private Context context;

    private AuthorizerHandler handler;

    @BeforeEach
    void setUp() {
        lenient().when(context.getLogger()).thenReturn(new NoOpLambdaLogger());
        handler = new AuthorizerHandler(new JwtService(SECRET));
    }

    @Test
    void authorizesValidToken() {

        String token = new JwtService(SECRET).issueClienteToken("84779441056", 1L);

        Map<String, Object> response = handler.handleRequest(eventWithAuthHeader("Bearer " + token), context);

        assertEquals(true, response.get("isAuthorized"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) response.get("context");
        assertEquals("84779441056", ctx.get("sub"));
        assertEquals("1", ctx.get("userId"));
        assertEquals("CLIENTE", ctx.get("roles"));
    }

    @Test
    void deniesMissingAuthorizationHeader() {

        Map<String, Object> response = handler.handleRequest(eventWithHeaders(new HashMap<>()), context);

        assertFalse((Boolean) response.get("isAuthorized"));
    }

    @Test
    void deniesHeaderWithoutBearerPrefix() {

        Map<String, Object> response = handler.handleRequest(eventWithAuthHeader("some-token"), context);

        assertFalse((Boolean) response.get("isAuthorized"));
    }

    @Test
    void deniesTokenSignedWithDifferentSecret() {

        String token = new JwtService("another-secret-key-with-32-chars!!!").issueClienteToken("84779441056", 1L);

        Map<String, Object> response = handler.handleRequest(eventWithAuthHeader("Bearer " + token), context);

        assertFalse((Boolean) response.get("isAuthorized"));
    }

    @Test
    void authorizationHeaderLookupIsCaseInsensitive() {

        String token = new JwtService(SECRET).issueClienteToken("84779441056", 1L);

        Map<String, Object> headers = new HashMap<>();
        headers.put("AUTHORIZATION", "Bearer " + token);

        Map<String, Object> response = handler.handleRequest(eventWithHeaders(headers), context);

        assertTrue((Boolean) response.get("isAuthorized"));
    }

    private static Map<String, Object> eventWithAuthHeader(String value) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("authorization", value);
        return eventWithHeaders(headers);
    }

    private static Map<String, Object> eventWithHeaders(Map<String, Object> headers) {
        return Map.of("headers", headers);
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
