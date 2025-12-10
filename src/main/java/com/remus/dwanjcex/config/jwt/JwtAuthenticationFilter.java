package com.remus.dwanjcex.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器。
 * 拦截所有请求，验证Authorization头中的JWT，并将用户信息存入ThreadLocal。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 18:15
 */
@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtUtils.validateToken(jwt)) {
                Long userId = jwtUtils.getUserIdFromToken(jwt);
                // 将用户ID存入ThreadLocal，供后续业务逻辑使用
                UserContextHolder.setCurrentUserId(userId);
            }

            // 继续执行过滤器链
            filterChain.doFilter(request, response);

        } finally {
            // 在请求处理完成后，必须清理ThreadLocal，防止内存泄漏
            UserContextHolder.clear();
        }
    }

    /**
     * 从HttpServletRequest中提取JWT。
     *
     * @param request 请求
     * @return JWT字符串，如果不存在则返回null
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
