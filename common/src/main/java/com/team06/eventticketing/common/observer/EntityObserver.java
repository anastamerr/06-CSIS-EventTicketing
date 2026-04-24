package com.team06.eventticketing.common.observer;

public interface EntityObserver {

    void onEvent(String action, Object payload);
}
