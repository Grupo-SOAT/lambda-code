package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

/**
 * Encaminha requisicoes que nao sao de autenticacao para o backend real
 * (o monolito atras do load balancer do EKS, {@code BACKEND_URL}).
 *
 * <p>A validacao/autorizacao do JWT continua sendo feita pelo
 * JwtAuthenticationFilter/RoleAuthorizationFilter do proprio monolito -
 * esta lambda so precisa repassar a requisicao como veio, headers
 * (incluindo Authorization) e body inclusos.
 */
public interface BackendProxyService {

    APIGatewayV2HTTPResponse forward(APIGatewayV2HTTPEvent event);
}
