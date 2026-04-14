package com.example.model;

import java.util.List;

public class User extends BaseEntity {
    private String name;
    private List<Order> orders;

    public String getName() {
        return name;
    }

    public List<Order> getOrders() {
        return orders;
    }
}