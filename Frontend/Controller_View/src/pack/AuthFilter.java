package pack;

import java.io.IOException;
import java.util.Set;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@WebFilter("/*")
public class AuthFilter implements Filter {
    private static final String BACKEND_BASE = System.getProperty("facade.api.url", "http://localhost:8080/facade");
    private static final Client REST_CLIENT = new ResteasyClientBuilder().build();
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/html/login.html",
            "/auth/login",
            "/auth/logout",
            "/auth/addAccount",
            "/auth/me"
    );

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (isAllowedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (isAuthenticated(req)) {
            chain.doFilter(request, response);
            return;
        }

        res.sendRedirect(req.getContextPath() + "/html/login.html");
    }

    @Override
    public void destroy() {
    }

    private boolean isAllowedPath(String path) {
        if (path == null) {
            return false;
        }
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("/auth/") || path.equals("/html/login.html");
    }

    private boolean isStaticResource(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/img/")
                || path.startsWith("/fonts/")
                || path.startsWith("/lib/")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".gif")
                || path.endsWith(".svg")
                || path.endsWith(".ico")
                || path.endsWith(".woff")
                || path.endsWith(".woff2")
                || path.endsWith(".ttf")
                || path.endsWith(".eot")
                || path.endsWith(".map");
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        try {
            Response backendResponse = REST_CLIENT.target(BACKEND_BASE + "/api/me")
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Cookie", request.getHeader("Cookie"))
                    .get();
            try {
                return backendResponse.getStatus() == HttpServletResponse.SC_OK;
            } finally {
                backendResponse.close();
            }
        } catch (Exception e) {
            return false;
        }
    }
}
