package edu.northeastern.cs6650.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsProxyController {

  private final String consumerUrl;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public AnalyticsProxyController(@Value("${consumer.url}") String consumerUrl) {
    this.consumerUrl = consumerUrl;
  }

  @GetMapping("/metrics/analytics")
  public ResponseEntity<String> proxy(HttpServletRequest request) throws Exception {
    String query = request.getQueryString();
    String target = consumerUrl + "/metrics/analytics" + (query != null ? "?" + query : "");

    HttpRequest proxyRequest = HttpRequest.newBuilder(URI.create(target)).GET().build();
    HttpResponse<String> response = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofString());

    return ResponseEntity.status(response.statusCode())
        .header("Content-Type", "application/json")
        .body(response.body());
  }
}
