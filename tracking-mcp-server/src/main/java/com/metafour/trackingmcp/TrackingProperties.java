package com.metafour.trackingmcp;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrlPattern default host pattern; {system} is replaced by the system name
 * @param systems        explicit overrides: system name (lower case) -> base URL
 * @param timeout        HTTP timeout
 * @param maxChars       max characters of tracking text returned to the LLM
 * @param contentSelector optional CSS selector to narrow the HTML to the tracking area (blank = whole body)
 */
@ConfigurationProperties(prefix = "tracking")
public record TrackingProperties(
        String baseUrlPattern,
        Map<String, String> systems,
        Duration timeout,
        Integer maxChars,
        String contentSelector) {

    public TrackingProperties {
        if (baseUrlPattern == null || baseUrlPattern.isBlank()) baseUrlPattern = "https://{system}.netcourier.net";
        if (systems == null) systems = Map.of();
        if (timeout == null) timeout = Duration.ofSeconds(15);
        if (maxChars == null) maxChars = 8000;
    }
}
