package com.rtxnano.ecommerce.user.security;

import com.rtxnano.ecommerce.user.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service responsible for generating and parsing cryptographically signed
 * JSON Web Tokens (JWT) using HMAC-SHA256 (HS256).
 *
 * Key Educational Concept:
 * Embedding roles directly inside the JWT payload ("roles" claim) eliminates
 * the need for receiving microservices to query a database on every request,
 * enabling true zero-DB stateless authorization across the microservice cluster.
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * Builds and signs a brand-new JWT containing both the user's email (subject)
     * and their authorized roles list.
     */
    public String generateToken(String email, Set<Role> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        List<String> roleNames = roles.stream()
                .map(Enum::name)
                .collect(Collectors.toList());

        return Jwts.builder()
                .subject(email)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Overloaded helper for generating a token with empty/default roles if needed.
     */
    public String generateToken(String email) {
        return generateToken(email, Set.of(Role.CUSTOMER));
    }

    /**
     * Extracts the subject (email) from an already-issued token.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the list of roles embedded in the token's "roles" claim.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> {
            List<?> rawRoles = claims.get("roles", List.class);
            if (rawRoles == null) {
                return List.of();
            }
            return rawRoles.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        });
    }

    /**
     * Confirms token signature and expiration validity.
     */
    public boolean isTokenValid(String token, String expectedEmail) {
        final String email = extractEmail(token);
        return email.equals(expectedEmail) && !isTokenExpired(token);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}