package edu.northeastern.cs6650.consumer.dto;

public class IdCount {
  private String id;
  private long count;

  public IdCount() {
  }

  public IdCount(String id, long count) {
    this.id = id;
    this.count = count;
  }

  public String getId() { return id; }
  public long getCount() { return count; }
  public void setId(String id) { this.id = id; }
  public void setCount(long count) { this.count = count; }
}
