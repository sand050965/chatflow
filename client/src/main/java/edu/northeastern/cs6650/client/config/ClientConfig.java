package edu.northeastern.cs6650.client.config;

import io.github.cdimascio.dotenv.Dotenv;

public final class ClientConfig {

  private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

  public static final String SERVER_URL;
  public static final String METRICS_URL;

  public static final int TOTAL_MESSAGES;

  public static final int WARMUP_THREADS = 32;

  public static final int WARMUP_MESSAGES_PER_THREAD = 1_000;

  public static final int MAIN_THREADS;

  public static final int MAX_RETRIES = 5;

  public static final int MESSAGE_QUEUE_SIZE = 10_000;

  public static final int ROOM_COUNT = 20;

  public static final int USER_COUNT = 100_000;

  public static final int CONNECT_TIMEOUT_SECONDS = 5;

  public static final int MESSAGE_POLL_TIMEOUT_SECONDS = 5;

  public static final int RETRY_BASE_BACKOFF_MS = 50;

  public static final int RETRY_BACKOFF_MULTIPLIER = 2;

  public static final int PROGRESS_LOG_INTERVAL = 10_000;

  public static final String CSV_FILE;

  public static final double MS_TO_SECONDS = 1000.0;

  public static final double TEXT_PROB = 0.90;

  public static final double JOIN_PROB = 0.95;

  public static final double PERCENTILE_95 = 0.95;

  public static final double PERCENTILE_99 = 0.99;

  public static final int MEDIAN_DIVISOR = 2;

  public static final long THROUGHPUT_BUCKET_SIZE_MS = 10_000;

  public static final double THROUGHPUT_BUCKET_TO_SECONDS = 1000.0;

  public static final double THROUGHPUT_DIVISOR = 10.0;

  public static final int MESSAGE_POOL_START_INDEX = 3;

  public static final int MESSAGE_POOL_SIZE = 50;

  static {
    String host = dotenv.get("SERVER_HOST");
    String port = dotenv.get("SERVER_PORT");
    if (host == null || host.isEmpty()) {
      host = "localhost";
    }
    if (port == null || port.isEmpty()) {
      port = "8080";
    }
    SERVER_URL = "ws://" + host + ":" + port + "/chat/";

    String metricsHost = dotenv.get("METRICS_HOST");
    String metricsPort = dotenv.get("METRICS_PORT");
    if (metricsHost == null || metricsHost.isEmpty()) {
      metricsHost = host;
    }
    if (metricsPort == null || metricsPort.isEmpty()) {
      metricsPort = "8081";
    }
    METRICS_URL = "http://" + metricsHost + ":" + metricsPort + "/metrics/analytics";

    String threads = dotenv.get("MAIN_THREADS");
    MAIN_THREADS = (threads != null && !threads.isEmpty())
        ? Integer.parseInt(threads) : 64;

    String totalMsgs = dotenv.get("TOTAL_MESSAGES");
    TOTAL_MESSAGES = (totalMsgs != null && !totalMsgs.isEmpty())
        ? Integer.parseInt(totalMsgs) : 500_000;

    CSV_FILE = "results/performance_metrics" + MAIN_THREADS + ".csv";
  }

  private ClientConfig() {
  }
}
