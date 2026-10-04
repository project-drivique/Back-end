package com.drivique.api.config;

import com.drivique.api.exception.ApiProblem;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ProblemSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;

    public ProblemSecurityHandler(@Autowired(required = false) ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        write(request, response, HttpStatus.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        write(request, response, HttpStatus.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiProblem.of(status, request.getRequestURI()));
    }
}
