package de.atra.kernsystem.api;

import de.atra.kernsystem.configuration.KernsystemProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    static final String HEADER = "x-api-key";

    private final KernsystemProperties properties;
    private final ObjectMapper mapper;

    public ApiKeyFilter(KernsystemProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (!properties.apiKey().equals(provided)) {
            log.debug("API key validation failed");
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8);
            response.getWriter().write(mapper.writeValueAsString(
                    ErrorHandling.response(HttpStatus.UNAUTHORIZED,
                            ErrorHandling.TYPE_AUTHENTICATION,
                            "Nicht authentifiziert",
                            "Header " + HEADER + " fehlt oder ist ungueltig").getBody()));
            return;
        }
        chain.doFilter(request, response);
    }
}
