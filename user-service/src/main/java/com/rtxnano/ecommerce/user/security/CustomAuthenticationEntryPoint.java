package com.rtxnano.ecommerce.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rtxnano.ecommerce.user.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

// AuthenticationEntryPoint is Spring Security's dedicated mechanism for
// "what should happen when an unauthenticated request hits a protected
// endpoint." Without this bean, Spring Security falls back to its
// default behavior — which, as we saw, produces a bare 403 with no
// useful body. This class lets us produce a proper 401 with our
// consistent ErrorResponse shape instead.
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Instantiate directly to guarantee it works regardless of Spring Context configuration
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Authentication is required to access this resource",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        // We write directly to the response here, since this runs at
        // the SERVLET/FILTER level, before Spring MVC's normal
        // @RestControllerAdvice machinery is even involved — this is a
        // different layer of the pipeline than GlobalExceptionHandler.
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
