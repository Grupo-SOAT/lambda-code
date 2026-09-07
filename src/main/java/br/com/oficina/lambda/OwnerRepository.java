package br.com.oficina.lambda;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Consulta a tabela {@code owners} do MESMO Postgres usado pelo monolito.
 *
 * <p>Limitacao conhecida: a tabela {@code owners} nao tem uma coluna de
 * status/ativo hoje (ver OwnerEntity.java no monolito), entao "status do
 * cliente" e tratado como existencia do CPF na base. Se um campo
 * {@code active} for adicionado no futuro, basta um {@code AND active = true}
 * na query abaixo.
 */
public class OwnerRepository {

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public OwnerRepository(String host, int port, String database, String user, String password) {
        this.jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(host, port, database);
        this.user = user;
        this.password = password;
    }

    public Optional<Owner> findByDocument(String document) throws SQLException {

        String sql = "SELECT owner_id, name, document, email FROM owners WHERE document = ?";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, document);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new Owner(
                        resultSet.getLong("owner_id"),
                        resultSet.getString("name"),
                        resultSet.getString("document"),
                        resultSet.getString("email")));
            }
        }
    }
}
