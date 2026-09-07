package br.com.oficina.lambda;

/**
 * Resolve valores a partir de ARNs de secrets. Interface para permitir
 * dublagem em teste sem depender do AWS Secrets Manager real.
 */
public interface SecretsResolver {

    String resolve(String secretArn);
}
