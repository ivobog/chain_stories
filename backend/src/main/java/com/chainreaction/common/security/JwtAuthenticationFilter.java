package com.chainreaction.common.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.chainreaction.common.error.ApiException;
import com.chainreaction.common.error.ErrorResponse;
import com.chainreaction.common.web.CorrelationIdFilter;
import com.chainreaction.user.domain.User;
import com.chainreaction.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                JwtClaims claims = jwtTokenService.validate(authorization.substring(7));
                User user = userRepository.findById(claims.userId()).orElse(null);
                if (user != null && user.isActive()) {
                    CurrentUserPrincipal principal = new CurrentUserPrincipal(user);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ApiException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(exception.getStatus().value());
                response.setContentType("application/json");
                objectMapper.writeValue(response.getOutputStream(),
                        ErrorResponse.of(exception.getErrorCode(), exception.getMessage(),
                                response.getHeader(CorrelationIdFilter.HEADER_NAME)));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
