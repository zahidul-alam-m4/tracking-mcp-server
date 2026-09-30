package com.metafour.trackingmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(TrackingProperties.class)
public class TrackingMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackingMcpApplication.class, args);
    }

    /** Registers every @Tool method of TrackingTools with the MCP server. */
    @Bean
    ToolCallbackProvider trackingToolCallbacks(TrackingTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
