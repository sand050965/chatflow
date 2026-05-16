package edu.northeastern.cs6650.client.dto;

public class ChatMessage {

  private String userId;

  private String username;

  private String message;

  private String timestamp;

  private MessageType messageType;

  /**
   * Constructs a ChatMessage with all required fields.
   *
   * @param userId      the user ID (1-100000)
   * @param username    the username (3-20 alphanumeric characters)
   * @param message     the message content (1-500 characters)
   * @param timestamp   the message timestamp in ISO-8601 format
   * @param messageType the message type (TEXT, JOIN, or LEAVE)
   */
  public ChatMessage(String userId, String username, String message, String timestamp, MessageType messageType) {
    this.userId = userId;
    this.username = username;
    this.message = message;
    this.timestamp = timestamp;
    this.messageType = messageType;
  }

  /**
   * Gets the user ID.
   *
   * @return the user ID
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Gets the username.
   *
   * @return the username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Gets the message content.
   *
   * @return the message content
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the message timestamp.
   *
   * @return the timestamp in ISO-8601 format
   */
  public String getTimestamp() {
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
}