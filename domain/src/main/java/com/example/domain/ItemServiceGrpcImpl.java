package com.example.domain;

import com.example.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

        try {
            com.example.domain.Item item = repository.findById(id);

            // Если  айтем не найден
            if (item == null) {
                log.warn("Item with id={} not found", id);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Item with ID " + id + " not found")
                        .asRuntimeException());
                return;
            }

            com.example.grpc.Item grpcItem = toProto(item);

            GetItemResponse resp = GetItemResponse.newBuilder()
                    .setItem(grpcItem)
                    .build();

            responseObserver.onNext(resp);
            responseObserver.onCompleted();
            log.debug("Item with id={} sent via gRPC", id);

        } catch (Exception e) {
            log.error("Error fetching item with id={} from the repository: {}", id, e.getMessage());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Failed to connect to repository or process request")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getAllItems(Empty request, StreamObserver<GetAllItemsResponse> responseObserver) {
        log.debug("gRPC getAllItems called");

        try {
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

        } catch (Exception e) {
            log.error("Error fetching all items from the repository: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to connect to repository or process request")
                    .withCause(e)
                    .asRuntimeException());
        }
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