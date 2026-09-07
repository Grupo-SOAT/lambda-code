package br.com.oficina.lambda;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Emite tokens no MESMO formato que
 * {@code br.com.fiap.postech.adapter.output.authentication.AuthenticationAdapter}
 * usa no monolito (mnl-oficina-mecanica): HS256, issuer
 * "mechanic-workshop-system", claims "roles" e "userId". O
 * JwtAuthenticationFilter do monolito aceita este token sem nenhuma mudanca
 * de codigo no monolito.
 */
public class JwtService {

    private static final String ISSUER = "mechanic-workshop-system";
    private static final long EXPIRATION_MINUTES = 30;

    private final SecretKey key;

    public JwtService(String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueClienteToken(String document, Long ownerId) {

        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(document)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("roles", List.of("CLIENTE"))
                .claim("userId", ownerId)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
