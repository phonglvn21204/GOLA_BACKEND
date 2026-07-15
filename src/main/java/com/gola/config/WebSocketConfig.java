package com.gola.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.stream.Stream;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final GolaProperties golaProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns(allowedOrigins().toArray(String[]::new))
            .withSockJS();
    }

    private List<String> allowedOrigins() {
        return Stream.of(
                "http://localhost:8081",
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:8081",
                "http://127.0.0.1:5173",
                "https://gola-way-ai-route.vercel.app",
                golaProperties.getFrontendUrl()
            )
            .filter(origin -> origin != null && !origin.isBlank())
            .distinct()
            .toList();
    }
}
