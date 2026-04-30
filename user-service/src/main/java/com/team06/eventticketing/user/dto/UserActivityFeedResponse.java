package com.team06.eventticketing.user.dto;

import java.util.ArrayList;
import java.util.List;

public class UserActivityFeedResponse {

    private List<UserActivityEventDTO> content = new ArrayList<>();
    private int page;
    private int size;
    private long totalElements;

    public UserActivityFeedResponse() {
    }

    public UserActivityFeedResponse(List<UserActivityEventDTO> content, int page, int size, long totalElements) {
        this.content = content == null ? new ArrayList<>() : new ArrayList<>(content);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<UserActivityEventDTO> getContent() {
        return content;
    }

    public void setContent(List<UserActivityEventDTO> content) {
        this.content = content == null ? new ArrayList<>() : new ArrayList<>(content);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
}
