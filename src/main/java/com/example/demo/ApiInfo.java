package com.example.demo;

import java.util.List;

public record ApiInfo(String name, String version, List<String> endpoints) {
}
