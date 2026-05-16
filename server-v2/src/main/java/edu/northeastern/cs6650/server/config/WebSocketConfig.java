package edu.northeastern.cs6650.server.config;

import edu.northeastern.cs6650.server.handler.ChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;import org.springframework.web.socket.config.annotation.WebSocketConfigurer;import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final ChatWebSocketHandler handler;

  public WebSocketConfig(ChatWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/chat/{roomId}")
        .setAllowedOrigins("*");
  }
}