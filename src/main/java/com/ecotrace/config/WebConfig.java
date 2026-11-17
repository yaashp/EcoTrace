package com.ecotrace.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.Set;

/**
 * Browser-facing protections:
 * - security headers on every response, including a content security policy that lists the few CDNs used;
 * - a CSRF guard: state-changing API calls must carry an X-Requested-With header. Browsers cannot add it
 *   to cross-site form posts, and the session cookie is SameSite=Lax;
 * - CORS only for explicitly configured origins (empty by default, since the page is served by this app).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self' https://cdnjs.cloudflare.com; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; "
            + "font-src https://fonts.gstatic.com; "
            + "img-src 'self' data: https://tile.openstreetmap.org; "
            + "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Value("${ecotrace.cors-origins:}")
    private String[] origins;

    @Value("${ecotrace.csp-enabled:true}")
    private boolean cspEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                if (cspEnabled) {
                    response.setHeader("Content-Security-Policy", CSP);
                }
                boolean api = request.getRequestURI().startsWith("/api/");
                if (api && !SAFE_METHODS.contains(request.getMethod())) {
                    String marker = request.getHeader("X-Requested-With");
                    if (marker == null || marker.isBlank()) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"message\":\"The request was blocked. Reload the page and try again.\"}");
                        return false;
                    }
                }
                return true;
            }
        }).addPathPatterns("/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (origins != null && origins.length > 0 && !origins[0].isBlank()) {
            registry.addMapping("/api/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT")
                    .allowedHeaders("Content-Type", "X-Requested-With")
                    .allowCredentials(true);
        }
    }
}
