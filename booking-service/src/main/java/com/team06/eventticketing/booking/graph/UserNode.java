package com.team06.eventticketing.booking.graph;

public class UserNode {

    private Long userId;
    private String name;

    public UserNode() {
    }

    public UserNode(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
