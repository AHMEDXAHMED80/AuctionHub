package com.example.auctionhub.auctionhub.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads .env files into the Spring Environment before application context creation.
 *
 * This is the modern Spring Boot 4.0+ approach using ApplicationListener.
 *
 * Behavior:
 * - Development: Loads .env file from project root
 * - Production: Skips if .env doesn't exist (uses system environment variables)
 * - Gracefully handles missing .env files
 */
public class DotEnvEnvironmentPostProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    private static final String DOTENV_FILE = ".env";
    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        // Try to load .env file from project root (development)
        Resource resource = new FileSystemResource(DOTENV_FILE);

        // If not found in root, try classpath (alternative location)
        if (!resource.exists()) {
            resource = new ClassPathResource(DOTENV_FILE);
        }

        // If .env file exists, load it
        if (resource.exists()) {
            try {
                Map<String, Object> properties = loadDotEnvFile(resource);
                MapPropertySource propertySource = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
                environment.getPropertySources().addLast(propertySource);

                System.out.println("✅ Loaded " + properties.size() + " properties from .env file");
            } catch (IOException e) {
                System.err.println("⚠️ Warning: Could not load .env file: " + e.getMessage());
                // Don't fail - continue with system environment variables
            }
        } else {
            System.out.println("ℹ️ No .env file found - using system environment variables");
        }
    }

    private Map<String, Object> loadDotEnvFile(Resource resource) throws IOException {
        Map<String, Object> properties = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse KEY=VALUE format
                int separatorIndex = line.indexOf('=');
                if (separatorIndex > 0) {
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();

                    // Remove surrounding quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    properties.put(key, value);
                } else {
                    System.err.println("⚠️ Warning: Invalid .env line " + lineNumber + ": " + line);
                }
            }
        }

        return properties;
    }
}
