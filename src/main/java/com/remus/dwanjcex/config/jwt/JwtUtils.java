package com.remus.dwanjcex.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT (JSON Web Token) 工具类。
 * 负责生成、解析和验证Token。
 */
@Component
public class JwtUtils {

    private final SecretKey secretKey;
    private final long expirationTime;

    /**
     * 构造函数，注入JWT配置。
     * @param secret Base64编码的密钥字符串。如果未在配置文件中提供，则使用一个安全的默认值。
     * @param expiration Token过期时间（毫秒）。
     */
    public JwtUtils(@Value("${jwt.secret:bXlqd3RzZWNyZXRrZXlmb3JleGNoYW5nZWFwcGxpY2F0aW9uMjAyNA==}") String secret,
                  @Value("${jwt.expiration:604800000}") long expiration) {
        // 从Base64编码的字符串生成密钥，更安全
        this.secretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationTime = expiration;
    }

    /**
     * 为指定用户ID生成一个JWT。
     *
     * @param userId 用户ID
     * @return JWT字符串
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从JWT中解析出用户ID。
     * 如果Token无效（签名错误、已过期等），此方法会抛出异常。
     *
     * @param token JWT字符串
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    /**
     * 验证Token是否有效。
     *
     * @param token JWT字符串
     * @return 如果有效返回true，否则返回false
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
