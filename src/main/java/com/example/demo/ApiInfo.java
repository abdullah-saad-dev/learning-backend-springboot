package com.example.demo;

import java.util.List;
import java.util.Map;

public record ApiInfo(String name, String version, Map<String, String> links, List<String> endpoints) {
}
