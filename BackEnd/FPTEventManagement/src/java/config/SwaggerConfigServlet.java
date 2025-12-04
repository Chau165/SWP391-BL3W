package config;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@WebServlet("/api/openapi.json")
public class SwaggerConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        // Prefer a static JSON file bundled in the webapp at /api/openapi.json.
        // This is safer than constructing JSON via Java string concatenation.
        InputStream is = getServletContext().getResourceAsStream("/api/openapi.json");

        try (PrintWriter out = resp.getWriter()) {
            if (is != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        out.println(line);
                    }
                    out.flush();
                    return;
                } catch (IOException e) {
                    // If reading fails, we'll fall through to a safe fallback JSON below.
                }
            }

            // Fallback: minimal, valid OpenAPI JSON (keeps Swagger UI from crashing).
            // NOTE: Replace this by the full JSON file at web/api/openapi.json for full docs.
            String fallback = "{"
                    + "\"openapi\":\"3.0.3\","
                    + "\"info\":{\"title\":\"FPT Event Management API\",\"version\":\"1.0.0\"},"
                    + "\"servers\":[{\"url\":\"http://localhost:8080/FPTEventManagement\"}],"
                    + "\"paths\":{},"
                    + "\"components\":{\"schemas\":{},\"securitySchemes\":{}}"
                    + "}";

            out.print(fallback);
            out.flush();
        }
    }
}