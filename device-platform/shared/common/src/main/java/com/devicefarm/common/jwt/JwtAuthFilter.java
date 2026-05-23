package com.devicefarm.common.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the Bearer JWT and populates SecurityContext with a {@link JwtPrincipal}.
 * Skips if header is absent — downstream filter chain decides whether endpoint allows anonymous.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenService tokens;

    public JwtAuthFilter(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            try {
                JwtPrincipal p = tokens.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + (p.role() == null ? "ANON" : p.role())))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtTokenService.InvalidJwtException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}
