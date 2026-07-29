package com.example.demo;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@RestController
public class MetaController {
    // Only our own handlers; springdoc and Boot's /error controller register mappings too.
    final static String BASE_PACKAGE = "com.example.demo";

    private final RequestMappingHandlerMapping mappings;
    private final String docsPath;
    private final String actuatorBasePath;

    // Actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping), so this must be qualified by bean name.
    public MetaController(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
                          @Value("${springdoc.swagger-ui.path:/swagger-ui.html}") String docsPath,
                          @Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {
        this.mappings = mappings;
        this.docsPath = docsPath;
        this.actuatorBasePath = actuatorBasePath;
    }

    @GetMapping("/")
    public Map<String, String> info() {
        return links();
    }

    private Map<String, String> links() {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("docs", docsPath);
        links.put("openapi", "/v3/api-docs");
        links.put("health", actuatorBasePath + "/health");
        return links;
    }


}
