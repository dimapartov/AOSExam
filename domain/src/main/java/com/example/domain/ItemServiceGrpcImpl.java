package com.example.domain;

import com.example.grpc.*;
import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ItemServiceGrpcImpl extends ItemServiceGrpc.ItemServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ItemServiceGrpcImpl.class);

    private final ItemRepository repository;

    public ItemServiceGrpcImpl(ItemRepository repository) {
        this.repository = repository;
    }

    @Override
    public void getItemById(GetItemRequest request, StreamObserver<GetItemResponse> responseObserver) {
        Long id = request.getId();
        log.debug("gRPC getItemById called with id={}", id);

        com.example.grpc.Item entity = toProto(repository.findById(id));
        if (entity == null) {
            // Пустой Item
            entity = com.example.grpc.Item.newBuilder().build();
            log.debug("Item with id={} not found, returning empty gRPC Item", id);
        } else {
            log.debug("Item with id={} found, returning gRPC Item", id);
        }

        GetItemResponse resp = GetItemResponse.newBuilder()
                .setItem(entity)
                .build();

        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    @Override
    public void getAllItems(Empty request, StreamObserver<GetAllItemsResponse> responseObserver) {
        log.debug("gRPC getAllItems called");

        List<com.example.grpc.Item> protoList = repository.findAll()
                .stream()
                .map(this::toProto)
                .collect(Collectors.toList());

        GetAllItemsResponse resp = GetAllItemsResponse.newBuilder()
                .addAllItems(protoList)
                .build();

        responseObserver.onNext(resp);
        responseObserver.onCompleted();
        log.debug("Returned total {} items via gRPC", protoList.size());
    }

    private com.example.grpc.Item toProto(com.example.domain.Item i) {
        if (i == null) return null;
        return com.example.grpc.Item.newBuilder()
                .setId(i.getId())
                .setTitle(i.getTitle() == null ? "" : i.getTitle())
                .setPrice(i.getPrice() == null ? 0.0 : i.getPrice())
                .setDescription(i.getDescription() == null ? "" : i.getDescription())
                .build();
    }
}