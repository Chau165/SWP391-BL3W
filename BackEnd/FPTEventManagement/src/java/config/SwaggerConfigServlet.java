package config;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/api/openapi.json")
public class SwaggerConfigServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");

        // JSON OpenAPI tối giản cho /api/login (đã validate hợp lệ)
        String json =
            "{" +
              "\"openapi\":\"3.0.1\"," +
              "\"info\":{\"title\":\"FPT Event Management API\",\"version\":\"1.0.0\"}," +
              "\"servers\":[{\"url\":\"http://localhost:8084/FPTEventManagement\"}]," +
              "\"paths\":{" +
                "\"/api/login\":{" +
                  "\"post\":{" +
                    "\"summary\":\"Login\"," +
                    "\"requestBody\":{" +
                      "\"required\":true," +
                      "\"content\":{" +
                        "\"application/json\":{" +
                          "\"schema\":{\"$ref\":\"#/components/schemas/LoginRequest\"}" +
                        "}" +
                      "}" +
                    "}," +
                    "\"responses\":{" +
                      "\"200\":{" +
                        "\"description\":\"OK\"," +
                        "\"content\":{" +
                          "\"application/json\":{" +
                            "\"schema\":{\"$ref\":\"#/components/schemas/LoginResponse\"}" +
                          "}" +
                        "}" +
                      "}," +
                      "\"401\":{\"description\":\"Unauthorized\"}" +
                    "}" +
                  "}" +
                "}" +
              "}," +
              "\"components\":{" +
                "\"schemas\":{" +
                  "\"LoginRequest\":{" +
                    "\"type\":\"object\"," +
                    "\"properties\":{" +
                      "\"email\":{\"type\":\"string\"}," +
                      "\"password\":{\"type\":\"string\"}" +
                    "}," +
                    "\"required\":[\"email\",\"password\"]" +
                  "}," +
                  "\"User\":{" +
                    "\"type\":\"object\"," +
                    "\"properties\":{" +
                      "\"id\":{\"type\":\"integer\"}," +
                      "\"fullName\":{\"type\":\"string\"}," +
                      "\"email\":{\"type\":\"string\"}," +
                      "\"phone\":{\"type\":\"string\"}," +
                      "\"role\":{\"type\":\"string\"}," +
                      "\"status\":{\"type\":\"string\"}" +
                    "}" +
                  "}," +
                  "\"LoginResponse\":{" +
                    "\"type\":\"object\"," +
                    "\"properties\":{" +
                      "\"status\":{\"type\":\"string\"}," +
                      "\"token\":{\"type\":\"string\"}," +
                      "\"user\":{\"$ref\":\"#/components/schemas/User\"}" +
                    "}" +
                  "}" +
                "}" +
              "}" +
            "}";

        try (PrintWriter out = resp.getWriter()) {
            out.print(json);
            out.flush();
        }
    }
}
