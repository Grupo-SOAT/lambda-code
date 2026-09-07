package br.com.oficina.lambda;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolve secrets via AWS Secrets Manager, com cache em memoria para
 * reaproveitar entre invocacoes do mesmo container (cold start).
 */
public class SecretsManagerResolver implements SecretsResolver {

    private final SecretsManagerClient client;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    public SecretsManagerResolver(SecretsManagerClient client) {
        this.client = client;
    }

    @Override
    public String resolve(String secretArn) {
        return cache.computeIfAbsent(secretArn, arn -> client.getSecretValue(
                GetSecretValueRequest.builder().secretId(arn).build()).secretString());
    }
}
