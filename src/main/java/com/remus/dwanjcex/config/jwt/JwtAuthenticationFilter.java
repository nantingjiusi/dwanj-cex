package com.remus.dwanjcex.config.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

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
                
                // 【关键修复】将认证信息放入Spring Security的上下文中
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId, // principal: 通常是用户ID或用户名
                        null,   // credentials: 对于JWT认证，这里是null
                        Collections.emptyList() // authorities: 权限列表，如果没有角色系统，则为空
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 为了兼容旧代码，仍然将用户ID存入ThreadLocal
                UserContextHolder.setCurrentUserId(userId);
            }

            filterChain.doFilter(request, response);

        } finally {
            // 在请求处理完成后，清理ThreadLocal
            UserContextHolder.clear();
            // SecurityContextHolder 会在请求结束时自动清理，无需手动操作
        }
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
