package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MetaController {
    final static String API_NAME = "Notes API";
    final static String API_VERSION = "1.0";
    final static List<String> ENDPOINTS = List.of("/api/notes");
    @GetMapping("/")
    public ApiInfo info() {
        return new ApiInfo(API_NAME, API_VERSION, ENDPOINTS);
    }
    @GetMapping("/health")
    public HealthInfo health() {
        return new HealthInfo("ok");
    }
}
