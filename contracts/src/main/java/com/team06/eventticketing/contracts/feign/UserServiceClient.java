package com.team06.eventticketing.contracts.feign;

import com.team06.eventticketing.contracts.dto.UserDTO;
import com.team06.eventticketing.contracts.feign.fallback.UserServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${feign.user-service.url}", fallback = UserServiceClientFallback.class)
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUser(@PathVariable("id") Long id);
}
