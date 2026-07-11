package com.rtxnano.ecommerce.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    // Pulled from application.properties, which reads it from .env
    // (JWT_SECRET). Never hardcoded — this is the shared secret used to
    // both sign new tokens and verify incoming ones.
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // How long a token stays valid, in milliseconds, before it expires.
    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // Converts our plain-text secret string into a proper cryptographic
    // SecretKey object that the JJWT library needs to sign/verify JWTs
    // using the HMAC-SHA algorithm family.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // Builds and signs a brand-new JWT for a given user's email. Called
    // once, right after a successful login.
    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)              // "sub" claim: who this token identifies
                .issuedAt(now)                // "iat" claim: creation timestamp
                .expiration(expiryDate)       // "exp" claim: when it stops being valid
                .signWith(getSigningKey())    // cryptographically signs the token
                .compact();                   // produces the final header.payload.signature string
    }

    // Reads the email (the "subject") out of an already-issued token.
    // Used later whenever we need to know "who made this request?"
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Confirms a token both belongs to the expected user AND hasn't
    // expired yet. This will be the core check our future JWT filter
    // uses on every protected request.
    public boolean isTokenValid(String token, String expectedEmail) {
        final String email = extractEmail(token);
        return email.equals(expectedEmail) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // Shared helper: parses a token, VERIFIES its signature (throws
    // automatically if the token was tampered with or signed with a
    // different secret), and then lets the caller decide which specific
    // piece of data to pull out via claimsResolver (e.g. subject,
    // expiration). This avoids duplicating the parse+verify logic in
    // every extraction method.
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }
}