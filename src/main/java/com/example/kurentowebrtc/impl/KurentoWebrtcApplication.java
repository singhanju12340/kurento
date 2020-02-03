package com.example.kurentowebrtc.impl;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@SpringBootApplication
@EnableWebSocket
public class KurentoWebrtcApplication implements WebSocketConfigurer {


    public static void main(String[] args) {
        SpringApplication.run(KurentoWebrtcApplication.class, args);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry.addHandler(handler(), "/hello");
    }

    @Bean
    public KurentoWebrtcHandler handler() {
        return new KurentoWebrtcHandler();
    }

    @Bean
    public UserSessionsRegistry registry() {
        return new UserSessionsRegistry();
    }

    @Bean
    public KurentoClient kurentoClient()
    {
        return KurentoClient.create();
    }
}
