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

        // 1. Попробуем взять из кеша
        Map<String, Object> cached = itemCacheService.getCachedItem(id);
        if (cached != null) {
            log.debug("Item id={} найден в Redis-кеше", id);
            return cached; // Возвращаем кешированное
        }

        // 2. Иначе идём по gRPC
        try {
            GetItemRequest req = GetItemRequest.newBuilder().setId(id).build();
            GetItemResponse resp = itemServiceBlockingStub.getItemById(req);
            com.example.grpc.Item item = resp.getItem();

            Map<String, Object> result = new HashMap<>();
            result.put("id", item.getId());
            result.put("title", item.getTitle());
            result.put("price", item.getPrice());
            result.put("description", item.getDescription());

            // 3. Кладём в кеш
            itemCacheService.cacheItem(item.getId(), result);

            log.info("Item id={} получен через gRPC и добавлен в кеш", id);
            return result;
        } catch (StatusRuntimeException ex) {
            log.error("Ошибка при получении Item id={} по gRPC: {}", id, ex.getMessage());
            return Collections.singletonMap("error", "Item not found");
        }
    }

    // GET /items
    @GetMapping
    public List<Map<String, Object>> getAllItems() {
        log.info("Request to GET all items");

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
        log.info("Всего получено {} items", list.size());
        return list;
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
            log.debug("Отправлено сообщение в RabbitMQ queue={}, operation={}, requestId={}", queueName, operation, requestId);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения в RabbitMQ: {}", e.getMessage());
            return Collections.singletonMap("error", "Failed to send message");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", 202);
        response.put("message", "Request accepted");
        response.put("requestId", requestId);
        return response;
    }
}