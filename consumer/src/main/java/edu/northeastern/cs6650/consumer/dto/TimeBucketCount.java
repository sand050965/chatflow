package edu.northeastern.cs6650.consumer.dto;

import java.time.Instant;

public class TimeBucketCount {
  private Instant bucket;
  private long count;

  public TimeBucketCount() {
  }

  public TimeBucketCount(Instant bucket, long count) {
    this.bucket = bucket;
    this.count = count;
  }

  public Instant getBucket() { return bucket; }
  public long getCount() { return count; }
  public void setBucket(Instant bucket) { this.bucket = bucket; }
  public void setCount(long count) { this.count = count; }
}
