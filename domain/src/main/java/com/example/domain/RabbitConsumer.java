package com.example.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RabbitConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RabbitConsumer.class);

    private static final String QUEUE_NAME = "item-queue";
    private static final String RABBIT_HOST = "rabbitmq"; // <-- убедитесь, что это реальный alias внутри docker-compose
    private static final int RABBIT_PORT = 5672;
    private static final String RABBIT_USER = "guest";
    private static final String RABBIT_PASS = "guest";

    private final ItemRepository repository;
    private final ObjectMapper mapper;

    public RabbitConsumer(ItemRepository repository) {
        this.repository = repository;
        this.mapper = new ObjectMapper();
    }

    @Override
    public void run() {
        log.info("RabbitConsumer is starting...");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT_HOST);
        factory.setPort(RABBIT_PORT);
        factory.setUsername(RABBIT_USER);
        factory.setPassword(RABBIT_PASS);

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            log.info("RabbitConsumer waiting for messages on queue '{}'", QUEUE_NAME);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                log.info("Received message: {}", msg);
                handleMessage(msg);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            CancelCallback cancelCallback = consumerTag -> {
                log.warn("Consumer cancelled: {}", consumerTag);
            };

            channel.basicConsume(QUEUE_NAME, false, deliverCallback, cancelCallback);

            // Блокируем поток, пока жив Consumer
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(1000);
            }

        } catch (IOException | TimeoutException | InterruptedException e) {
            log.error("RabbitConsumer stopped due to error", e);
        }
    }

    private void handleMessage(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String operation = root.get("operation").asText();
            JsonNode itemNode = root.get("item");

            log.debug("Handling message with operation={}", operation);

            switch (operation) {
                case "CREATE" -> {
                    Item item = new Item();
                    item.setTitle(itemNode.get("title").asText());
                    item.setPrice(itemNode.get("price").asDouble());
                    item.setDescription(itemNode.get("description").asText());
                    repository.save(item);
                    log.info("Created item with ID={}", item.getId());
                }
                case "UPDATE" -> {
                    Long id = itemNode.get("id").asLong();
                    Item existing = repository.findById(id);
                    if (existing != null) {
                        existing.setTitle(itemNode.get("title").asText());
                        existing.setPrice(itemNode.get("price").asDouble());
                        existing.setDescription(itemNode.get("description").asText());
                        repository.update(existing);
                        log.info("Updated item with ID={}", id);
                    } else {
                        log.warn("Item with ID={} not found for update", id);
                    }
                }
                case "DELETE" -> {
                    Long id = itemNode.get("id").asLong();
                    repository.delete(id);
                    log.info("Deleted item with ID={}", id);
                }
                default -> {
                    log.warn("Unknown operation: {}", operation);
                }
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

}