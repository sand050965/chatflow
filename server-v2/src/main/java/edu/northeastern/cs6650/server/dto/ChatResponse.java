package edu.northeastern.cs6650.server.dto;

public class ChatResponse {

  private String status;

  private String message;

  private String serverTimestamp;

  private ChatMessage originalMessage;

  /**
   * Default constructor for ChatResponse.
   */
  public ChatResponse() {
  }

  public ChatResponse(String status, String message, String serverTimestamp,
                      ChatMessage originalMessage) {
    this.status = status;
    this.message = message;
    this.serverTimestamp = serverTimestamp;
    this.originalMessage = originalMessage;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }

  public void setServerTimestamp(String serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
  }

  public ChatMessage getOriginalMessage() {
    return originalMessage;
  }

  public void setOriginalMessage(ChatMessage originalMessage) {
    this.originalMessage = originalMessage;
  }
}