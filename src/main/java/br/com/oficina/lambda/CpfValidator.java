package br.com.oficina.lambda;

/**
 * Replica exata do algoritmo de digito verificador de
 * {@code br.com.fiap.postech.domain.owner.validation.DocumentValidator#isValidCPF}
 * no monolito (mnl-oficina-mecanica), para que um CPF aceito aqui seja
 * sempre um CPF que o monolito tambem aceitaria.
 */
public final class CpfValidator {

    private CpfValidator() {
    }

    public static String normalize(String document) {
        if (document == null) {
            return "";
        }
        return document.replaceAll("\\D", "");
    }

    public static boolean isValid(String document) {

        String cpf = normalize(document);

        if (cpf.length() != 11) {
            return false;
        }

        if (cpf.matches("(\\d)\\1{10}")) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (cpf.charAt(i) - '0') * (10 - i);
        }
        int firstDigit = 11 - (sum % 11);
        if (firstDigit >= 10) {
            firstDigit = 0;
        }
        if (firstDigit != (cpf.charAt(9) - '0')) {
            return false;
        }

        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += (cpf.charAt(i) - '0') * (11 - i);
        }
        int secondDigit = 11 - (sum % 11);
        if (secondDigit >= 10) {
            secondDigit = 0;
        }

        return secondDigit == (cpf.charAt(10) - '0');
    }
}
