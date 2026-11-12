package es.storeapp.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void loginSuccess(String email, HttpServletRequest request) {
        log.info("LOGIN_SUCCESS email={} ip={}", email, clientIp(request));
    }

    public void loginFailure(String email, HttpServletRequest request, String reason) {
        log.warn("LOGIN_FAILURE email={} ip={} reason={}", email, clientIp(request), reason);
    }

    public void accessDenied(String principal, String resource, HttpServletRequest request) {
        log.warn("ACCESS_DENIED principal={} resource={} ip={}", principal, resource, clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }
}