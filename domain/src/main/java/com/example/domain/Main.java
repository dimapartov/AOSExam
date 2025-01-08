package com.example.domain;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting Domain (non-SpringBoot) service...");

        // Инициализируем Hibernate (SessionFactory)
        HibernateUtil.getSessionFactory();

        // Запускаем gRPC-сервер на порту 8081
        Server server = NettyServerBuilder.forPort(8081)
                .addService(new ItemServiceGrpcImpl(new ItemRepository()))
                .build()
                .start();

        log.info("gRPC server started, listening on port 8081");

        // Запускаем RabbitMQ Consumer в отдельном потоке
        RabbitConsumer rabbitConsumer = new RabbitConsumer(new ItemRepository());
        Thread consumerThread = new Thread(rabbitConsumer, "RabbitConsumerThread");
        consumerThread.start();
        log.info("RabbitConsumer thread started");

        // Ожидаем завершения gRPC-сервера
        server.awaitTermination();
        log.info("gRPC server terminated");
    }
}