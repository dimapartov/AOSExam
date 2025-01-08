package com.example.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Double price;
    private String description;

    public Item() {
    }

    public Item(String title, Double price, String description) {
        this.title = title;
        this.price = price;
        this.description = description;
    }

    // Getters/Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) { this.id = id; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) { this.title = title; }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) { this.price = price; }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) { this.description = description; }
}