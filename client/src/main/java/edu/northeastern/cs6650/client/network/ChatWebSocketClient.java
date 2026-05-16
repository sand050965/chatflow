package edu.northeastern.cs6650.client.network;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ChatWebSocketClient extends WebSocketClient {

  private volatile CountDownLatch        responseLatch;
  private final AtomicReference<String>  lastResponse;
  private final AtomicReference<Boolean> lastResponseSuccess;
  private volatile long                  sendTimestamp;

  public ChatWebSocketClient(URI serverUri) {
    super(serverUri);
    this.lastResponse        = new AtomicReference<>();
    this.lastResponseSuccess = new AtomicReference<>(false);
  }

  @Override public void onOpen(ServerHandshake h) {}

  @Override
  public void onMessage(String message) {
    lastResponse.set(message);

    boolean isChatResponse = message.contains("\"status\":");
    if (!isChatResponse) return;

    boolean ok = message.contains("\"status\":\"SUCCESS\"") ||
        message.contains("\"status\": \"SUCCESS\"");
    lastResponseSuccess.set(ok);
    if (responseLatch != null) responseLatch.countDown();
  }

  @Override public void onClose(int code, String reason, boolean remote) {}

  @Override
  public void onError(Exception ex) {
    System.err.println("⚠ WebSocket Error: " + ex.getMessage());
    lastResponseSuccess.set(false);
    if (responseLatch != null) responseLatch.countDown();
  }

  public boolean sendAndWait(String message, long timeoutMs) throws InterruptedException {
    responseLatch = new CountDownLatch(1);
    lastResponse.set(null);
    lastResponseSuccess.set(false);
    sendTimestamp = System.currentTimeMillis();  // record before send
    send(message);
    boolean got = responseLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    return got && lastResponseSuccess.get();
  }

  /** ms from send() to ack received. */
  public long getLastLatency()    { return System.currentTimeMillis() - sendTimestamp; }
  public long getSendTimestamp()  { return sendTimestamp; }
  public String getLastResponse() { return lastResponse.get(); }
}