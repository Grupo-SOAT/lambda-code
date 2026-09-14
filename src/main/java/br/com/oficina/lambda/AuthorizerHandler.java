package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import io.jsonwebtoken.Claims;

import java.util.List;
import java.util.Map;

/**
 * Lambda Authorizer (REQUEST, payload format 2.0, simple responses) das
 * rotas protegidas do API Gateway (ver modules/aws/gateway no repo
 * k8s-infra-oficina-mecanica). O Gateway encaminha o pedido direto para o
 * backend via HTTP_PROXY - esta lambda so decide se autoriza ou nao,
 * lendo o header {@code Authorization: Bearer <token>}.
 *
 * <p>Evento e resposta sao tratados como {@code Map} cru (em vez de um POJO
 * do aws-lambda-java-events) porque o unico campo usado e "headers", e o
 * formato de resposta simples do payload 2.0 e so
 * {@code {isAuthorized, context}}.
 */
public class AuthorizerHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final JwtService jwtService;

    public AuthorizerHandler() {
        this.jwtService = new JwtService(requireEnv("JWT_SECRET"));
    }

    AuthorizerHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {

        String authHeader = extractAuthorizationHeader(event);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return deny();
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        if (token.isEmpty()) {
            return deny();
        }

        try {
            Claims claims = jwtService.verify(token);

            List<?> roles = claims.get("roles", List.class);
            String rolesCsv = roles == null ? "" : String.join(",", roles.stream().map(Object::toString).toList());

            return Map.of(
                    "isAuthorized", true,
                    "context", Map.of(
                            "sub", claims.getSubject() == null ? "" : claims.getSubject(),
                            "userId", String.valueOf(claims.get("userId")),
                            "roles", rolesCsv));

        } catch (Exception e) {
            context.getLogger().log("{\"event\":\"authorizer_denied\",\"reason\":\"%s\"}%n"
                    .formatted(e.getClass().getSimpleName()));
            return deny();
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractAuthorizationHeader(Map<String, Object> event) {

        Object headersObj = event.get("headers");

        if (!(headersObj instanceof Map<?, ?> headers)) {
            return null;
        }

        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            if (entry.getKey() instanceof String key && key.equalsIgnoreCase("authorization")) {
                return entry.getValue() == null ? null : entry.getValue().toString();
            }
        }

        return null;
    }

    private static Map<String, Object> deny() {
        return Map.of("isAuthorized", false);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + name);
        }
        return value;
    }
}
