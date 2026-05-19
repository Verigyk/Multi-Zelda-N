package pack;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@WebServlet("/auth/*")
public class AuthServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String BACKEND_BASE = System.getProperty("facade.api.url", "http://localhost:8080/facade");
    private static final Client REST_CLIENT = new ResteasyClientBuilder().build();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = getPath(request);
        if ("/me".equals(path)) {
            proxyGet(response, "/api/me", request);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = getPath(request);
        switch (path) {
            case "/login":
                proxyPost(request, response, "/auth/login");
                break;
            case "/logout":
                proxyPost(request, response, "/auth/logout");
                break;
            case "/addAccount":
                proxyPost(request, response, "/auth/addAccount");
                break;
            default:
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                break;
        }
    }

    private String getPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            return "";
        }
        return path;
    }

    private void proxyGet(HttpServletResponse response, String backendPath, HttpServletRequest request)
            throws IOException {
        Response backendResponse = REST_CLIENT.target(BACKEND_BASE + backendPath)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", request.getHeader("Cookie"))
                .get();
        copyResponse(backendResponse, response);
    }

    private void proxyPost(HttpServletRequest request, HttpServletResponse response, String backendPath)
            throws IOException {
        String body = readRequestBody(request);
        Response backendResponse = REST_CLIENT.target(BACKEND_BASE + backendPath)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("Cookie", request.getHeader("Cookie"))
                .post(Entity.entity(body == null ? "" : body, MediaType.APPLICATION_JSON_TYPE));
        copyResponse(backendResponse, response);
    }

    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void copyResponse(Response backendResponse, HttpServletResponse response) throws IOException {
        try {
            response.setStatus(backendResponse.getStatus());
            for (Map.Entry<String, List<Object>> header : backendResponse.getHeaders().entrySet()) {
                String name = header.getKey();
                for (Object value : header.getValue()) {
                    if ("Set-Cookie".equalsIgnoreCase(name)) {
                        response.addHeader(name, String.valueOf(value));
                    } else if ("Content-Type" .equalsIgnoreCase(name)) {
                        response.setContentType(String.valueOf(value));
                    } else if ("Location".equalsIgnoreCase(name)) {
                        response.setHeader(name, String.valueOf(value));
                    }
                }
            }
            String entity = backendResponse.hasEntity() ? backendResponse.readEntity(String.class) : null;
            if (entity != null && !entity.isEmpty()) {
                if (response.getContentType() == null) {
                    response.setContentType(MediaType.TEXT_PLAIN + ";charset=UTF-8");
                }
                response.getWriter().write(entity);
            }
        } finally {
            backendResponse.close();
        }
    }
}
