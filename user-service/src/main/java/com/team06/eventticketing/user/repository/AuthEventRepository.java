package com.team06.eventticketing.user.repository;

import com.team06.eventticketing.common.observer.AuthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {

    Page<AuthEvent> findByUserId(Long userId, Pageable pageable);
}
