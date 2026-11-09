package es.storeapp.web.interceptors;

import es.storeapp.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.servlet.HandlerInterceptor;

public class CsrfInterceptor implements HandlerInterceptor {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        HttpSession session = request.getSession(true);

        // 1. Asegura que la sesión tiene un token (lo crea la primera vez)
        String token = (String) session.getAttribute(Constants.CSRF_TOKEN_SESSION);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(Constants.CSRF_TOKEN_SESSION, token);
        }

        // 2. Solo valida en métodos que modifican estado (POST, PUT, DELETE, PATCH)
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {

            String requestToken = request.getParameter(Constants.CSRF_TOKEN_PARAM);
            if (requestToken == null || !token.equals(requestToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return false;   // corta la petición
            }
        }
        return true;
    }
}