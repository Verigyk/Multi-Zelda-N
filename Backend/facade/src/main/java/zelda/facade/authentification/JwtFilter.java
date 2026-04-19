package zelda.facade.authentification;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String servletPath = request.getServletPath();
        if (servletPath != null && servletPath.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getCookies() != null && request.getCookies().length > 0) {
            Cookie cookie_token = null;

            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("Token")) {
                    cookie_token = cookie;
                    break;
                }
            }
            
            if (cookie_token != null) {
                try {
                    String token = cookie_token.getValue();
                    String username = jwtUtil.extractUsername(token);

                    var auth = new UsernamePasswordAuthenticationToken(
                            username, null, List.of());

                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (JwtException | IllegalArgumentException ex) {
                    SecurityContextHolder.clearContext();
                    Cookie expired = new Cookie("Token", "");
                    expired.setPath("/");
                    expired.setHttpOnly(true);
                    expired.setMaxAge(0);
                    response.addCookie(expired);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
