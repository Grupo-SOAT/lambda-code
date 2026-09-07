package br.com.oficina.lambda;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpfValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"84779441056", "847.794.410-56", "39544511075", "395.445.110-75"})
    void acceptsKnownValidCpfs(String cpf) {
        assertTrue(CpfValidator.isValid(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111111", "00000000000", "12345678900", "847.794.410-55"})
    void rejectsInvalidCpfs(String cpf) {
        assertFalse(CpfValidator.isValid(cpf));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(CpfValidator.isValid("123"));
    }

    @Test
    void rejectsNull() {
        assertFalse(CpfValidator.isValid(null));
    }

    @Test
    void normalizeStripsNonDigits() {
        assertEquals("84779441056", CpfValidator.normalize("847.794.410-56"));
    }

    @Test
    void normalizeOfNullIsEmpty() {
        assertEquals("", CpfValidator.normalize(null));
    }
}
