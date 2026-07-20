package com.rag.ingestion.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_KEY = System.getenv("JWT_SECRET");
    private static final long EXPIRATION_TIME = 86400000; 
    
    public String generateToken(com.rag.ingestion.domain.User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("tenant_id", user.getTenant().getId()) 
                .claim("role", user.getRole())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSignInKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenant_id", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractExpiration(token).before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
            java.util.Base64.getEncoder().encodeToString(SECRET_KEY.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}