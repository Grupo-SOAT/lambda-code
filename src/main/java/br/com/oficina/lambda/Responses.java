package br.com.oficina.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.util.Map;

final class Responses {

    private Responses() {
    }

    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");

    static APIGatewayV2HTTPResponse ok(String jsonBody) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(JSON_HEADERS)
                .withBody(jsonBody)
                .build();
    }

    static APIGatewayV2HTTPResponse error(int statusCode, String code, String message) {
        String body = "{\"error\":\"%s\",\"message\":\"%s\"}".formatted(code, escape(message));
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(JSON_HEADERS)
                .withBody(body)
                .build();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }
}
