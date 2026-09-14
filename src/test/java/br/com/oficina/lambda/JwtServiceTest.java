package br.com.oficina.lambda;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-characters!!";

    @Test
    void issuesTokenWithMonolithCompatibleClaims() {

        JwtService jwtService = new JwtService(SECRET);

        String token = jwtService.issueClienteToken("84779441056", 1L);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        assertEquals("84779441056", claims.getSubject());
        assertEquals("mechanic-workshop-system", claims.getIssuer());
        assertEquals(List.of("CLIENTE"), claims.get("roles", List.class));
        assertEquals(1, ((Number) claims.get("userId")).intValue());
        assertTrue(claims.getExpiration().after(claims.getIssuedAt()));
    }

    @Test
    void tokenSignedWithDifferentSecretFailsVerification() {

        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.issueClienteToken("84779441056", 1L);

        SecretKey wrongKey = Keys.hmacShaKeyFor("another-secret-key-with-32-chars!!!".getBytes(StandardCharsets.UTF_8));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> Jwts.parser().verifyWith(wrongKey).build().parseSignedClaims(token));
    }
    @Test
    void rejectsMissingExpiryAndWrongIssuerAndExpiredTokens() {
        var service = new JwtService(SECRET);
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var missingExpiry = Jwts.builder().subject("client").issuer("mechanic-workshop-system").signWith(key, Jwts.SIG.HS256).compact();
        var wrongIssuer = Jwts.builder().subject("client").issuer("other").expiration(new java.util.Date(System.currentTimeMillis()+60000)).signWith(key, Jwts.SIG.HS256).compact();
        var expired = Jwts.builder().subject("client").issuer("mechanic-workshop-system").expiration(new java.util.Date(1000)).signWith(key, Jwts.SIG.HS256).compact();
        for (String token : List.of(missingExpiry, wrongIssuer, expired)) {
            org.junit.jupiter.api.Assertions.assertThrows(io.jsonwebtoken.JwtException.class, () -> service.verify(token));
        }
        assertEquals("84779441056", service.verify(service.issueClienteToken("84779441056", 1L)).getSubject());
    }}
