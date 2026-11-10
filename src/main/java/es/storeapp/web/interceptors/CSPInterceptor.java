package es.storeapp.web.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.SecureRandom;
import java.util.Base64;

public class CSPInterceptor implements HandlerInterceptor {


    private static final SecureRandom RANDOM = new SecureRandom();


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
        throws Exception {

        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute("cspNonce", nonce);

        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                        "img-src 'self' data:; " +
                        "script-src 'self' 'nonce-" + nonce + "'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "object-src 'none'; " +
                        "frame-ancestors 'self'; " +
                        "base-uri 'self';");
        return true;
    }
    
}
