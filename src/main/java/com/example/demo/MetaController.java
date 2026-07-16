package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;

@RestController
public class MetaController {
    final static String API_NAME = "Tasks API";
    final static String API_VERSION = "1.0";
    // Only our own handlers; springdoc and Boot's /error controller register mappings too.
    final static String BASE_PACKAGE = "com.example.demo";

    private final RequestMappingHandlerMapping mappings;

    public MetaController(RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @GetMapping("/")
    public ApiInfo info() {
        return new ApiInfo(API_NAME, API_VERSION, endpoints());
    }

    @GetMapping("/health")
    public HealthInfo health() {
        return new HealthInfo("ok");
    }

    private List<String> endpoints() {
        return mappings.getHandlerMethods().entrySet().stream()
                .filter(e -> e.getValue().getBeanType().getPackageName().startsWith(BASE_PACKAGE))
                .flatMap(e -> {
                    Set<String> paths = e.getKey().getPathPatternsCondition().getPatternValues();
                    Set<String> verbs = e.getKey().getMethodsCondition().getMethods().stream()
                            .map(Enum::name)
                            .collect(java.util.stream.Collectors.toSet());
                    // A @RequestMapping with no method answers every verb.
                    Set<String> methods = verbs.isEmpty() ? Set.of("ANY") : verbs;
                    return paths.stream().flatMap(path -> methods.stream().map(m -> m + " " + path));
                })
                .distinct()
                .sorted()
                .toList();
    }
}
