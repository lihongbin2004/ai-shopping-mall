package com.mall.security;

import com.mall.common.result.R;
import com.mall.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write(JsonUtil.toJson(R.unauthorized()));
            writer.flush();
            return false;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token)) {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write(JsonUtil.toJson(R.unauthorized()));
            writer.flush();
            return false;
        }

        Long userId = jwtUtil.parseUserId(token);
        String role = jwtUtil.parseRole(token);
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);

        // /api/admin/** 仅允许管理员访问
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            response.setContentType("application/json;charset=utf-8");
            PrintWriter writer = response.getWriter();
            writer.write(JsonUtil.toJson(R.error(403, "无权限访问")));
            writer.flush();
            return false;
        }
        return true;
    }
}
