package edu.northeastern.cs6650.server.service;

import edu.northeastern.cs6650.server.dto.QueueMessage;

public interface MessageProducer {
  boolean publish(QueueMessage msg);

  long getPublishSuccess();

  long getPublishFailure();
}
