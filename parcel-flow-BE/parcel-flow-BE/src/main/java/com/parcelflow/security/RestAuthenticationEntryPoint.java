package com.parcelflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiResponse<Void> body = ApiResponse.failure(
                ErrorCode.AUTH_UNAUTHENTICATED.name(),
                "Authentication required",
                List.of(),
                request.getRequestURI());
        response.setStatus(ErrorCode.AUTH_UNAUTHENTICATED.getStatus().value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
