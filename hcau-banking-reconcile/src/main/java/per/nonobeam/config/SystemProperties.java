package per.nonobeam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "system")
public record SystemProperties(String userId) {}
