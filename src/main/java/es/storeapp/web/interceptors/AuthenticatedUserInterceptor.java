package es.storeapp.web.interceptors;

import es.storeapp.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;
import es.storeapp.common.SecurityAuditLogger;

public class AuthenticatedUserInterceptor implements HandlerInterceptor {

    private final SecurityAuditLogger audit;

    public AuthenticatedUserInterceptor(SecurityAuditLogger audit) {
        this.audit = audit;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, 
                             HttpServletResponse response, 
                             Object handler)
            throws Exception {

        HttpSession session = request.getSession();
        if(session.getAttribute(Constants.USER_SESSION) == null) {
            audit.accessDenied("anonymous", request.getRequestURI(), request);
            response.sendRedirect(request.getContextPath() + Constants.LOGIN_ENDPOINT +
                    Constants.PARAMS + Constants.NEXT_PAGE + Constants.PARAM_VALUE + request.getRequestURI());
            return false;
        }
        return true;
    }
}
