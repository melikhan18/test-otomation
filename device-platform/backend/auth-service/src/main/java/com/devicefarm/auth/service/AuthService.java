package com.devicefarm.auth.service;

import com.devicefarm.auth.api.dto.AuthDtos;
import com.devicefarm.auth.domain.RefreshToken;
import com.devicefarm.auth.domain.RefreshTokenRepository;
import com.devicefarm.auth.domain.User;
import com.devicefarm.auth.domain.UserRepository;
import com.devicefarm.common.error.ApiException;
import com.devicefarm.common.jwt.JwtProperties;
import com.devicefarm.common.jwt.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final JwtProperties jwtProps;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder encoder, JwtTokenService tokens, JwtProperties jwtProps) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.tokens = tokens;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public AuthDtos.LoginResponse login(String username, String password) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("invalid credentials"));
        if (!user.isEnabled()) throw ApiException.unauthorized("user disabled");
        if (!encoder.matches(password, user.getPasswordHash()))
            throw ApiException.unauthorized("invalid credentials");

        String access = tokens.issueUserAccessToken(user.getId(), user.getRole(), user.getProductId());
        String refresh = issueRefreshToken(user);

        return new AuthDtos.LoginResponse(
                access, refresh,
                jwtProps.getAccessTokenTtl().toSeconds(),
                user.getId(), user.getUsername(), user.getRole(), user.getProductId());
    }

    @Transactional
    public AuthDtos.LoginResponse refresh(String refreshTokenJwt) {
        Claims claims;
        try {
            claims = tokens.parseClaims(refreshTokenJwt);
        } catch (JwtTokenService.InvalidJwtException e) {
            throw ApiException.unauthorized("invalid refresh token");
        }
        if (!"refresh".equals(claims.get("type"))) {
            throw ApiException.unauthorized("not a refresh token");
        }

        String hash = sha256(refreshTokenJwt);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("refresh token unknown"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("refresh token expired/revoked");
        }

        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("user not found"));

        // rotate
        stored.revoke();
        String newRefresh = issueRefreshToken(user);
        String access = tokens.issueUserAccessToken(user.getId(), user.getRole(), user.getProductId());

        return new AuthDtos.LoginResponse(
                access, newRefresh,
                jwtProps.getAccessTokenTtl().toSeconds(),
                user.getId(), user.getUsername(), user.getRole(), user.getProductId());
    }

    private String issueRefreshToken(User user) {
        String token = tokens.issueRefreshToken(user.getId(), user.getProductId());
        Instant expires = Instant.now().plus(jwtProps.getRefreshTokenTtl());
        refreshTokens.save(new RefreshToken(user.getId(), sha256(token), expires));
        return token;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
