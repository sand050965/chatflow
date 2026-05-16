package edu.northeastern.cs6650.server.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
  public static final String EXCHANGE    = "chat.exchange";
  public static final int    ROOM_COUNT  = 20;
  public static final int    MESSAGE_TTL = 60_000;   // 1 min — stale chat is useless
  public static final int    MAX_LENGTH  = 100_000;  // max queued messages per room

  @Bean
  public TopicExchange chatExchange() {
    return ExchangeBuilder.topicExchange(EXCHANGE)
        .durable(true)
        .build();
  }

  @Bean
  public Declarables roomQueuesAndBindings() {
    java.util.List<Declarable> items = new java.util.ArrayList<>();

    for (int i = 1; i <= ROOM_COUNT; i++) {
      String name = "room." + i;

      Queue queue = QueueBuilder.durable(name)
          .withArgument("x-message-ttl", MESSAGE_TTL)
          .withArgument("x-max-length",  MAX_LENGTH)
          .withArgument("x-overflow",    "drop-head")  // drop oldest on overflow
          .build();

      Binding binding = BindingBuilder
          .bind(queue)
          .to(chatExchange())
          .with(name);

      items.add(queue);
      items.add(binding);
    }

    return new Declarables(items);
  }
}
