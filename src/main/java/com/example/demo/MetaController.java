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
    final static String API_NAME = "Tasks API";
    final static String API_VERSION = "1.0";
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
    public ApiInfo info() {
        return new ApiInfo(API_NAME, API_VERSION, links(), endpoints());
    }

    // Neither of these shows up in endpoints(): springdoc is filtered out by package,
    // and actuator answers from its own handler mapping entirely. Hence the hand-built list.
    private Map<String, String> links() {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("docs", docsPath);
        links.put("openapi", "/v3/api-docs");
        links.put("health", actuatorBasePath + "/health");
        return links;
    }

    private List<String> endpoints() {
        // A TreeSet sorts and de-duplicates as we insert, so no cleanup pass afterwards.
        // Duplicates are real: one handler method can be registered under several paths.
        var lines = new TreeSet<String>();

        // Each entry is one handler method: the key describes how it is reached
        // (paths + verbs), the value describes the method itself.
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();

            // Skip everything we did not write. Springdoc and Boot's built-in /error
            // controller register handlers here too, and listing them would be noise.
            if (!handler.getBeanType().getPackageName().startsWith(BASE_PACKAGE)) {
                continue;
            }

            var info = entry.getKey();
            var verbs = info.getMethodsCondition().getMethods();

            // One line per path/verb pair: @GetMapping({"/a", "/b"}) is two lines.
            for (var path : info.getPathPatternsCondition().getPatternValues()) {
                if (verbs.isEmpty()) {
                    // No verb declared means @RequestMapping answers every verb,
                    // so there is no single method name to print.
                    lines.add("ANY " + path);
                } else {
                    // RequestMethod is an enum whose toString() is already "GET", "POST", ...
                    for (var verb : verbs) {
                        lines.add(verb + " " + path);
                    }
                }
            }
        }
        return List.copyOf(lines);
    }
}
