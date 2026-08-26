package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Entry point da lambda unica (rota {@code $default} do API Gateway, ver
 * modules/aws/gateway no repo k8s-infra-oficina-mecanica):
 *
 * <ul>
 *   <li>{@code POST /auth/cpf} - valida o CPF, consulta a tabela
 *       {@code owners} e emite um JWT compativel com o monolito;</li>
 *   <li>qualquer outra rota - repassada como esta (proxy transparente) para
 *       o backend real ({@code BACKEND_URL}), que continua validando o JWT
 *       via seu proprio JwtAuthenticationFilter/RoleAuthorizationFilter.</li>
 * </ul>
 */
public class ValidatorHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String AUTH_PATH = "/auth/cpf";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OwnerRepository ownerRepository;
    private final JwtService jwtService;
    private final BackendProxyService backendProxyService;

    public ValidatorHandler() {

        SecretsResolver secrets = new SecretsManagerResolver(SecretsManagerClient.create());

        String dbHost = requireEnv("DATABASE_HOST");
        int dbPort = Integer.parseInt(System.getenv().getOrDefault("DATABASE_PORT", "5432"));
        String dbName = System.getenv().getOrDefault("DATABASE_NAME", "workshop");
        String dbUser = secrets.resolve(requireEnv("DATABASE_USER_SECRET_ARN"));
        String dbPassword = secrets.resolve(requireEnv("DATABASE_PASSWORD_SECRET_ARN"));
        String jwtSecret = secrets.resolve(requireEnv("JWT_SECRET_ARN"));

        this.ownerRepository = new OwnerRepository(dbHost, dbPort, dbName, dbUser, dbPassword);
        this.jwtService = new JwtService(jwtSecret);
        this.backendProxyService = new HttpBackendProxyService(requireEnv("BACKEND_URL"));
    }

    ValidatorHandler(OwnerRepository ownerRepository, JwtService jwtService, BackendProxyService backendProxyService) {
        this.ownerRepository = ownerRepository;
        this.jwtService = jwtService;
        this.backendProxyService = backendProxyService;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {

        String path = event.getRawPath();
        String method = event.getRequestContext().getHttp().getMethod();

        context.getLogger().log("{\"path\":\"%s\",\"method\":\"%s\"}%n".formatted(path, method));

        if ("POST".equalsIgnoreCase(method) && AUTH_PATH.equals(path)) {
            return handleCpfAuth(event.getBody());
        }

        return backendProxyService.forward(event);
    }

    private APIGatewayV2HTTPResponse handleCpfAuth(String requestBody) {

        String cpf;
        try {
            JsonNode json = OBJECT_MAPPER.readTree(requestBody == null ? "{}" : requestBody);
            cpf = json.path("cpf").asText(null);
        } catch (Exception e) {
            return Responses.error(400, "INVALID_CPF", "Corpo da requisicao invalido");
        }

        if (cpf == null || cpf.isBlank()) {
            return Responses.error(400, "INVALID_CPF", "CPF e obrigatorio");
        }

        String normalized = CpfValidator.normalize(cpf);

        if (!CpfValidator.isValid(normalized)) {
            return Responses.error(400, "INVALID_CPF", "CPF invalido");
        }

        Optional<Owner> owner;
        try {
            owner = ownerRepository.findByDocument(normalized);
        } catch (SQLException e) {
            return Responses.error(500, "INTERNAL_ERROR", "Falha ao consultar cliente");
        }

        if (owner.isEmpty()) {
            return Responses.error(404, "CLIENT_NOT_FOUND", "Cliente nao encontrado para o CPF informado");
        }

        String token = jwtService.issueClienteToken(owner.get().document(), owner.get().id());

        String body = """
                {"token":"%s","ownerId":%d,"name":"%s"}""".formatted(
                token, owner.get().id(), owner.get().name().replace("\"", "'"));

        return Responses.ok(body);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variavel de ambiente obrigatoria ausente: " + name);
        }
        return value;
    }
}
