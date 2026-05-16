package edu.northeastern.cs6650.client.generator;

import edu.northeastern.cs6650.client.config.ClientConfig;
import edu.northeastern.cs6650.client.dto.ChatMessage;
import edu.northeastern.cs6650.client.dto.MessageType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class MessageGenerator implements Runnable {

  private final BlockingQueue<ChatMessage> messageQueue;
  private final List<String> messagePool;
  private final Random random;

  private final ConcurrentHashMap<Integer, Boolean> userJoinedState = new ConcurrentHashMap<>();

  public MessageGenerator(BlockingQueue<ChatMessage> messageQueue) {
    this.messageQueue = messageQueue;
    this.messagePool  = generateMessagePool();
    this.random       = new Random();
  }

  @Override
  public void run() {
    try {
      for (int i = 0; i < ClientConfig.TOTAL_MESSAGES; i++) {
        messageQueue.put(generateMessage());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private ChatMessage generateMessage() {
    int userId   = random.nextInt(ClientConfig.USER_COUNT) + 1;
    String username  = "user" + userId;
    String message   = messagePool.get(random.nextInt(messagePool.size()));
    String timestamp = Instant.now().toString();

    boolean joined = userJoinedState.getOrDefault(userId, false);
    MessageType messageType;

    if (!joined) {
      messageType = MessageType.JOIN;
      userJoinedState.put(userId, true);
    } else {
      double rand = random.nextDouble();
      if (rand < 0.90) {
        messageType = MessageType.TEXT;
      } else {
        messageType = MessageType.LEAVE;
        userJoinedState.put(userId, false);
      }
    }

    return new ChatMessage(String.valueOf(userId), username, message, timestamp, messageType);
  }

  private List<String> generateMessagePool() {
    List<String> pool = new ArrayList<>();
    pool.add("Hello everyone!");
    pool.add("How are you all doing?");
    pool.add("Great to be here!");
    for (int i = ClientConfig.MESSAGE_POOL_START_INDEX; i < ClientConfig.MESSAGE_POOL_SIZE; i++) {
      pool.add("Test message number " + i);
    }
    return pool;
  }
}