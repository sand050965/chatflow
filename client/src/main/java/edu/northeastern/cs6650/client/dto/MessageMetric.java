package edu.northeastern.cs6650.client.dto;

public class MessageMetric {

  private long timestamp;

  private MessageType messageType;

  private long latencyMs;

  private String statusCode;

  private int roomId;

  /**
   * Constructs a MessageMetric with all performance data.
   *
   * @param timestamp   the epoch milliseconds when message was sent
   * @param messageType the type of message (TEXT, JOIN, or LEAVE)
   * @param latencyMs   the round-trip latency in milliseconds
   * @param statusCode  the result status (typically "SUCCESS" or "FAILURE")
   * @param roomId      the chat room ID where message was sent
   */
  public MessageMetric(long timestamp, MessageType messageType, long latencyMs, String statusCode, int roomId) {
    this.timestamp = timestamp;
    this.messageType = messageType;
    this.latencyMs = latencyMs;
    this.statusCode = statusCode;
    this.roomId = roomId;
  }

  /**
   * Gets the timestamp when the message was sent.
   *
   * @return epoch milliseconds timestamp
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the message type.
   *
   * @return the message type (TEXT, JOIN, or LEAVE)
   */
  public MessageType getMessageType() {
    return messageType;
  }

  /**
   * Gets the round-trip latency.
   *
   * @return latency in milliseconds from send to acknowledgment
   */
  public long getLatencyMs() {
    return latencyMs;
  }

  /**
   * Gets the status code indicating success or failure.
   *
   * @return the status code (e.g., "SUCCESS", "FAILURE")
   */
  public String getStatusCode() {
    return statusCode;
  }

  /**
   * Gets the room ID where the message was sent.
   *
   * @return the chat room ID (1-20)
   */
  public int getRoomId() {
    return roomId;
  }
}