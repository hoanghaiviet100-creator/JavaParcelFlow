package com.parcelflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ApiResponse<Void> body = ApiResponse.failure(
                ErrorCode.AUTH_FORBIDDEN.name(),
                "Access denied",
                List.of(),
                request.getRequestURI());
        response.setStatus(ErrorCode.AUTH_FORBIDDEN.getStatus().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
