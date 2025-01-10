package com.example.gateway.controller;

import com.example.gateway.service.ItemCacheService;
import com.example.grpc.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@RestController
@RequestMapping("/items")
public class ItemController {
    private static final Logger log = LoggerFactory.getLogger(ItemController.class);

    private final ItemServiceGrpc.ItemServiceBlockingStub itemServiceBlockingStub;
    private final AmqpTemplate amqpTemplate;
    private final ObjectMapper objectMapper;
    private final ItemCacheService itemCacheService;

    @Value("${spring.rabbitmq.template.default-receive-queue}")
    private String queueName;

    public ItemController(ItemServiceGrpc.ItemServiceBlockingStub itemServiceBlockingStub,
                          AmqpTemplate amqpTemplate,
                          ObjectMapper objectMapper,
                          ItemCacheService itemCacheService) {
        this.itemServiceBlockingStub = itemServiceBlockingStub;
        this.amqpTemplate = amqpTemplate;
        this.objectMapper = objectMapper;
        this.itemCacheService = itemCacheService;
    }

    // GET /items/{id}
    @GetMapping("/{id}")
    public Map<String, Object> getItemById(@PathVariable Long id) {
        log.info("Request to GET item by id={}", id);

        // 1. Кеш
        Map<String, Object> cached = itemCacheService.getCachedItem(id);
        if (cached != null) {
            log.debug("Item id={} found in Redis cache", id);
            return cached;
        }

        // 2. gRPC
        try {
            GetItemRequest req = GetItemRequest.newBuilder().setId(id).build();
            GetItemResponse resp = itemServiceBlockingStub.getItemById(req);
            com.example.grpc.Item item = resp.getItem();

            Map<String, Object> result = new HashMap<>();
            result.put("id", item.getId());
            result.put("title", item.getTitle());
            result.put("price", item.getPrice());
            result.put("description", item.getDescription());

            // Кешируем
            itemCacheService.cacheItem(item.getId(), result);

            log.info("Item id={} retrieved via gRPC and cached", id);
            return result;
        } catch (StatusRuntimeException ex) {
            log.error("Error retrieving Item id={} via gRPC: {}", id, ex.getMessage());
            return createErrorResponse("Failed to get item with provided id",404);
        } catch (Exception ex) {
            log.error("Unexpected error retrieving Item id={} via gRPC: {}", id, ex.getMessage());
            return createErrorResponse("Unexpected server error", 500);
        }
    }

    // GET /items
    @GetMapping
    public List<Map<String, Object>> getAllItems() {
        log.info("Request to GET all items");
        try {
            GetAllItemsResponse resp = itemServiceBlockingStub.getAllItems(Empty.newBuilder().build());
            List<Map<String, Object>> list = new ArrayList<>();
            for (com.example.grpc.Item item : resp.getItemsList()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", item.getId());
                map.put("title", item.getTitle());
                map.put("price", item.getPrice());
                map.put("description", item.getDescription());
                list.add(map);
            }
            log.info("Total items retrieved: {}", list.size());
            return list;
        } catch (StatusRuntimeException ex) {
            log.error("Error retrieving all items via gRPC: {}", ex.getMessage());
            throw new RuntimeException("gRPC service is unavailable.", ex);
        }
    }

    // POST /items
    @PostMapping
    public Map<String, Object> createItem(@RequestBody Map<String, Object> itemData) {
        log.info("Request to CREATE item: {}", itemData);
        return sendAsyncOperation("CREATE", itemData);
    }

    // PUT /items/{id}
    @PutMapping("/{id}")
    public Map<String, Object> updateItem(@PathVariable Long id, @RequestBody Map<String, Object> itemData) {
        log.info("Request to UPDATE item id={}, data={}", id, itemData);
        itemData.put("id", id);
        return sendAsyncOperation("UPDATE", itemData);
    }

    // DELETE /items/{id}
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteItem(@PathVariable Long id) {
        log.info("Request to DELETE item id={}", id);
        Map<String, Object> itemData = new HashMap<>();
        itemData.put("id", id);
        return sendAsyncOperation("DELETE", itemData);
    }

    private Map<String, Object> sendAsyncOperation(String operation, Map<String, Object> itemData) {
        String requestId = UUID.randomUUID().toString();

        Map<String, Object> messageBody = new HashMap<>();
        messageBody.put("operation", operation);
        messageBody.put("item", itemData);
        messageBody.put("requestId", requestId);

        try {
            String jsonMsg = objectMapper.writeValueAsString(messageBody);
            amqpTemplate.convertAndSend(queueName, jsonMsg);
            log.debug("Message sent to RabbitMQ queue={}, operation={}, requestId={}", queueName, operation, requestId);
        } catch (Exception e) {
            log.error("Error sending message to RabbitMQ: {}", e.getMessage());
            return createErrorResponse("Failed to connect to RabbitMQ server", 503);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", 202);
        response.put("message", "Request accepted");
        response.put("requestId", requestId);
        return response;
    }

    private Map<String, Object> createErrorResponse(String errorMessage, int statusCode) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", statusCode);
        errorResponse.put("error", errorMessage);
        return errorResponse;
    }
}