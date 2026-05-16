package edu.northeastern.cs6650.consumer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsRefreshService {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsRefreshService.class);

  private final JdbcTemplate jdbcTemplate;

  @Value("${analytics.refresh-enabled:true}")
  private boolean refreshEnabled;

  public AnalyticsRefreshService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Scheduled(
      initialDelayString = "${analytics.refresh-initial-delay-ms:30000}",
      fixedDelayString = "${analytics.refresh-interval-ms:60000}")
  public void refreshMaterializedViews() {
    if (!refreshEnabled) {
      return;
    }

    try {
      jdbcTemplate.execute("SELECT refresh_analytics_views()");
      log.debug("[ANALYTICS_REFRESH] materialized views refreshed");
    } catch (Exception e) {
      log.warn("[ANALYTICS_REFRESH_FAIL] error={}", e.getMessage());
    }
  }
}
