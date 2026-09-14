package br.com.oficina.lambda;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnerRepositoryTest {
    @Test void readsInactiveStatusWithoutDiscardingClient() throws Exception {
        try (var driver = mockStatic(DriverManager.class)) {
            var connection = mock(Connection.class);
            var statement = mock(PreparedStatement.class);
            var result = mock(ResultSet.class);
            driver.when(() -> DriverManager.getConnection("jdbc:postgresql://db:5432/workshop", "user", "pass")).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            when(result.next()).thenReturn(true);
            when(result.getLong("owner_id")).thenReturn(1L);
            when(result.getBoolean("active")).thenReturn(false);
            var owner = new OwnerRepository("db",5432,"workshop","user","pass").findByDocument("84779441056");
            assertTrue(owner.isPresent());
            assertFalse(owner.get().active());
            verify(statement).setString(1,"84779441056");
            verify(connection).close();
            verify(statement).close();
            verify(result).close();
        }
    }
}