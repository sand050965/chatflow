package edu.northeastern.cs6650.server.dto;

public class ErrorResponse {

  private String status;

  private String error;

  private String serverTimestamp;

  public ErrorResponse() {
  }

  public ErrorResponse(String status, String error, String serverTimestamp) {
    this.status = status;
    this.error = error;
    this.serverTimestamp = serverTimestamp;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getServerTimestamp() {
    return serverTimestamp;
  }

  public void setServerTimestamp(String serverTimestamp) {
    this.serverTimestamp = serverTimestamp;
  }
}